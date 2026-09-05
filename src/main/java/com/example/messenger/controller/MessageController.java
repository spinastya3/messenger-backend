package com.example.messenger.controller;

import com.example.messenger.crypto.SessionKeyManager;
import com.example.messenger.dto.MessageDto;
import com.example.messenger.service.MessageService;
import com.example.messenger.service.PushNotificationService;
import com.example.messenger.model.Message;
import com.example.messenger.model.MessageStatus;
import com.example.messenger.model.User;
import com.example.messenger.repository.MessageRepository;
import com.example.messenger.repository.UserRepository;
import com.example.messenger.crypto.EncryptionUtil;
import io.swagger.v3.oas.annotations.Operation;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@RestController
@RequiredArgsConstructor
@CrossOrigin(origins = "*", allowedHeaders = "*")
public class MessageController {

    private final SimpMessagingTemplate messagingTemplate; // Пересылает сообщения
    private final MessageRepository messageRepository; // Где хранятся сообщения в БД
    private final UserRepository userRepository; // Пользователи
    private final PushNotificationService pushNotificationService; // Пуши
    private final MessageService messageService; // Пагинация
    private final EncryptionUtil encryptionUtil; // Шифрование сообщений
    private final SessionKeyManager sessionKeyManager;



    @MessageMapping("/chat.send")
    public void processMessage(@Payload Message message) {

        System.out.println("🔍 [ДО СОХРАНЕНИЯ] Ссылка от мобилки: " + message.getImageUrl());

        // 🛡️ Валидация юзеров из БД
        if (message.getSender() != null && message.getSender().getId() != null) {
            User realSender = userRepository.findById(message.getSender().getId())
                    .orElseThrow(() -> new IllegalArgumentException("Отправитель не найден в БД"));
            message.setSender(realSender);
        }

        if (message.getRecipient() != null && message.getRecipient().getId() != null) {
            User realRecipient = userRepository.findById(message.getRecipient().getId())
                    .orElseThrow(() -> new IllegalArgumentException("Получатель не найден в БД"));
            message.setRecipient(realRecipient);
        }

        message.setTimestamp(LocalDateTime.now());
        message.setStatus(MessageStatus.SENT);

        String clearText = "";

        if (message.getContent() != null) {
            String senderUsername = message.getSender().getUsername();
            String recipientUsername = message.getRecipient().getUsername();

            String senderSessionKey = sessionKeyManager.getKey(senderUsername);
            String recipientSessionKey = sessionKeyManager.getKey(recipientUsername);

            // 1. Расшифровываем входящий пакет от мобилки
            clearText = encryptionUtil.decrypt(message.getContent(), senderSessionKey);

            // 2. Сохраняем в БД чистый текст (JPA-конвертер сам зашифрует его для диска)
            message.setContent(clearText);
            messageRepository.save(message);

            // 3. Отправляем ПОЛУЧАТЕЛЮ персональный сетевой шифр
            if (recipientSessionKey != null) {
                String encryptedForRecipient = encryptionUtil.encrypt(clearText, recipientSessionKey);

                // Чтобы не портить оригинальный объект message по ссылке,
                // временно подставляем шифр получателя, стреляем в сокет и сразу возвращаем чистый текст обратно!
                message.setContent(encryptedForRecipient);
                messagingTemplate.convertAndSend("/topic/messages." + message.getRecipient().getId(), message);
                message.setContent(clearText); // Вернули чистый текст!
            }

            // 4. Отправляем ОТПРАВИТЕЛЮ (эхо-подтверждение) его персональный сетевой шифр
            if (senderSessionKey != null) {
                String encryptedForSender = encryptionUtil.encrypt(clearText, senderSessionKey);

                message.setContent(encryptedForSender);
                messagingTemplate.convertAndSend("/topic/messages." + message.getSender().getId(), message);
                message.setContent(clearText); // Вернули чистый текст!
            }

        } else {
            // Если это медиафайл без текста — просто сохраняем и делаем стандартную сокет-рассылку
            messageRepository.save(message);

            if (message.getRecipient() != null && message.getRecipient().getId() != null) {
                messagingTemplate.convertAndSend("/topic/messages." + message.getRecipient().getId(), message);
            }
            if (message.getSender() != null && message.getSender().getId() != null) {
                messagingTemplate.convertAndSend("/topic/messages." + message.getSender().getId(), message);
            }
        }

        System.out.println("🔍 [ПОСЛЕ СОХРАНЕНИЯ] Ссылка из БД: " + message.getImageUrl());

        // 🔥 СТАРЫЕ ДУБЛИРУЮЩИЕ СТРОКИ ОТПРАВКИ СОКЕТОВ ОТСЮДА ПОЛНОСТЬЮ УДАЛЕНЫ!
        // Теперь на эмулятор никогда не прилетит второй вредоносный фрейм.

        // 🚀 ЛОГИКА ПУШЕЙ (Здесь в переменной clearText гарантированно лежит красивый чистый текст!)
        try {
            User recipient = message.getRecipient();
            if (recipient != null) {
                Optional<User> recipientFromDb = userRepository.findById(recipient.getId());

                if (recipientFromDb.isPresent() && recipientFromDb.get().getFcmToken() != null) {
                    String targetToken = recipientFromDb.get().getFcmToken();
                    String senderName = "Пользователь";

                    if (message.getSender() != null && message.getSender().getId() != null) {
                        Optional<User> senderFromDb = userRepository.findById(message.getSender().getId());
                        if (senderFromDb.isPresent()) {
                            senderName = senderFromDb.get().getUsername();
                        }

                        String title = senderName;
                        String body = (clearText != null && !clearText.isEmpty()) ? clearText : message.getContent();

                        if (message.getImageUrl() != null && !message.getImageUrl().isEmpty()) {
                            if (body == null || body.trim().isEmpty()) {
                                body = "Фотография";
                            }
                        }

                        pushNotificationService.sendPushNotification(
                                targetToken,
                                title,
                                body,
                                message.getSender().getId(),
                                senderName
                        );
                    }
                }
            }
        } catch(Exception e){
            System.err.println("🟨 Не удалось отправить пуш-уведомление: " + e.getMessage());
        }
    }



    // 1. Сюда приходят новые сообщения от отправителя
//    @MessageMapping("/chat.send")
//    public void processMessage(@Payload Message message) {
//
//        System.out.println("🔍 [ДО СОХРАНЕНИЯ] Ссылка от мобилки: " + message.getImageUrl());
//
//        if (message.getSender() != null && message.getSender().getId() != null) {
//            User realSender = userRepository.findById(message.getSender().getId())
//                    .orElseThrow(() -> new IllegalArgumentException("Отправитель не найден в БД"));
//            message.setSender(realSender);
//        }
//
//        if (message.getRecipient() != null && message.getRecipient().getId() != null) {
//            User realRecipient = userRepository.findById(message.getRecipient().getId())
//                    .orElseThrow(() -> new IllegalArgumentException("Получатель не найден в БД"));
//            message.setRecipient(realRecipient);
//        }
//
//        // Записываем время на сервере в сообщение
//        message.setTimestamp(LocalDateTime.now());
//
//        // Ставим статус SENT в БД для нового сообщения
//        message.setStatus(MessageStatus.SENT);
//
//        String clearText = "";
//
//        // 🚀 ИДЕАЛЬНОЕ СИММЕТРИЧНОЕ ШИФРОВАНИЕ ДЛЯ БАЗЫ:
//        if (message.getContent() != null) {
//
//            String senderUsername = message.getSender().getUsername();
//            String recipientUsername = message.getRecipient().getUsername();
//            String senderSessionKey = sessionKeyManager.getKey(senderUsername);
//            String recipientSessionKey = sessionKeyManager.getKey(recipientUsername);
//
//            clearText = encryptionUtil.decrypt(message.getContent(), senderSessionKey);
//
//            message.setContent(clearText);
//            messageRepository.save(message);
//            if (recipientSessionKey != null) {
//                message.setContent(encryptionUtil.encrypt(clearText, recipientSessionKey));
//                messagingTemplate.convertAndSend("/topic/messages." + message.getRecipient().getId(), message);
//            }
//
//            if (senderSessionKey != null) {
//                message.setContent(encryptionUtil.encrypt(clearText, senderSessionKey));
//                messagingTemplate.convertAndSend("/topic/messages." + message.getSender().getId(), message);
//            }
//
//        } else {
//            // Если это фото или видео без текста — просто сохраняем как есть
//            messageRepository.save(message);
//        }
//
//        System.out.println("🔍 [ПОСЛЕ СОХРАНЕНИЯ] Ссылка из БД: " + message.getImageUrl());
//
//        if (message.getRecipient() != null && message.getRecipient().getId() != null) {
//            messagingTemplate.convertAndSend("/topic/messages." + message.getRecipient().getId(), message);
//        }
//
//        // Шлем обратно отправителю (чтобы синее облачко на эмуляторе отобразило чистый текст и галочку)
//        if (message.getSender() != null && message.getSender().getId() != null) {
//            messagingTemplate.convertAndSend("/topic/messages." + message.getSender().getId(), message);
//        }
//        // Шлём пуш-уведомление
//        try {
//            User recipient = message.getRecipient();
//
//            if (recipient != null) {
//                Optional<User> recipientFromDb = userRepository.findById(recipient.getId());
//
//                if (recipientFromDb.isPresent() && recipientFromDb.get().getFcmToken() != null) {
//
//                    String targetToken = recipientFromDb.get().getFcmToken();
//                    String senderName = "Пользователь";
//
//                    if (message.getSender() != null && message.getSender().getId() != null) {
//                        Optional<User> senderFromDb = userRepository.findById(message.getSender().getId());
//                        if (senderFromDb.isPresent()) {
//                            senderName = senderFromDb.get().getUsername();
//                        }
//
//                        String title = senderName;
//                        String body = (clearText != null && !clearText.isEmpty()) ? clearText : message.getContent();
//                        if (message.getImageUrl() != null && !message.getImageUrl().isEmpty()) {
//                            if (body == null || body.trim().isEmpty()) {
//                                body = "Фотография";
//                            }
//                        }
//
//                        pushNotificationService.sendPushNotification(
//                                targetToken,
//                                title,
//                                body,
//                                message.getSender().getId(),
//                                senderName
//                        );
//                    }
//                }
//            }
//        } catch(Exception e){
//            System.err.println("🟨 Не удалось отправить пуш-уведомление: " + e.getMessage());
//        }
//    }

    @PostMapping("/api/chat/status/delivered")
    @Operation(
            summary = " [HTTP POST] Отметить сообщения от собеседника как доставленные через Пуш",
            description = "Вызывается фоновым сервисом Android-клиента (MyFirebaseMessagingService) при получении пуша, когда само приложение закрыто. Переводит сообщения в статус DELIVERED и оповещает отправителя по сокету."
    )
    public ResponseEntity<Void> markAsDeliveredHttp(
            @RequestParam Long senderId,
            @RequestParam Long recipientId) {

        if (senderId == null || senderId <= 0 || recipientId == null || recipientId <= 0) {
            return ResponseEntity.badRequest().build();
        }

        System.out.println("📥 [БЭКЕНД] Фоновый HTTP запрос доставки. Отправитель: " + senderId + " | Получатель (мы): " + recipientId);

        // Ищем в базе данных сообщения от друга к нам, которые еще в статусе SENT или NULL
        List<Message> sentMessages = messageRepository.findSentMessages(senderId, recipientId);

        if (!sentMessages.isEmpty()) {
            for (Message msg : sentMessages) {
                msg.setStatus(MessageStatus.DELIVERED);
            }
            // Массово сохраняем обновленные статусы сообщений в БД
            messageRepository.saveAll(sentMessages);

            // Его телефон поймает этот пакет, и у него на экране одна серая галочка
            // без перезаходов и морганий сменится на две белые галочки доставки!
            messagingTemplate.convertAndSend("/topic/messages.status." + senderId, Map.of(
                    "status", "DELIVERED",
                    "recipientId", recipientId,
                    "senderId", senderId
            ));

            System.out.println("📩 [БЭКЕНД] Сообщения от " + senderId + " успешно переведены в DELIVERED через фоновый HTTP!");
        }
        return ResponseEntity.ok().build();
    }

    @MessageMapping("/chat.read")
    public void readMessages(@Payload Map<String, Long> payload) {
        Long senderId = payload.get("senderId");
        Long recipientId = payload.get("recipientId");

        if (senderId == null || recipientId == null) return;

        // Ищем непрочитанные в базе
        List<Message> unreadMessages = messageRepository.findUnreadMessages(senderId, recipientId);

        if (!unreadMessages.isEmpty()) {
            for (Message msg : unreadMessages) {
                msg.setStatus(MessageStatus.READ);
            }
            messageRepository.saveAll(unreadMessages);
            System.out.println("👥 [БАЗА] Статусы сообщений обновлены в READ для юзера " + recipientId);
        }

        messagingTemplate.convertAndSend("/topic/messages.status." + senderId, Map.of(
                "status", "READ",
                "recipientId", recipientId,
                "senderId", senderId
        ));

        System.out.println("🚀 [СОКЕТ] Сигнал READ успешно отправлен в топик /topic/messages.status." + senderId);
    }

    @GetMapping("/api/chat/history")
    public ResponseEntity<?> getChatHistory(
            @RequestParam Long senderId,
            @RequestParam Long recipientId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            Principal principal) { // 🔥 Спринг автоматически подставит сюда авторизованного юзера!

        if (senderId == null || senderId <= 0 || recipientId == null || recipientId <= 0) {
            return ResponseEntity
                    .badRequest()
                    .body("Ошибка 400: Некорректные ID пользователей");
        }

        if (principal == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Пользователь не авторизован");
        }

        System.out.println("📥 [БЭКЕНД] HTTP запрос истории. Собеседники: " + senderId + " и " + recipientId + " | Страница: " + page);

        // 1. Получаем чистую историю из базы (Hibernate уже расшифровал её ключом БД)
        List<Message> history = messageService.getChatHistory(senderId, recipientId, page, size);

        // 2. Узнаем имя пользователя, который сделал этот HTTP-запрос
        String requesterUsername = principal.getName();
        String userSessionKey = sessionKeyManager.getKey(requesterUsername);

        if (userSessionKey == null) {
            System.err.println("❌ [БЭКЕНД] Критическая ошибка: сессионный ключ для пользователя " + requesterUsername + " не найден в памяти!");
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Ошибка безопасности: сессия не найдена");
        }

        List<MessageDto> dtoList = new ArrayList<>();

        for (Message msg : history) {
            // Шифруем текст, если он есть
            String networkContent = msg.getContent();
            if (networkContent != null && !networkContent.trim().isEmpty()) {
                networkContent = encryptionUtil.encrypt(msg.getContent(), userSessionKey);
            }

            // Собираем DTO
            MessageDto dto = MessageDto.builder()
                    .id(msg.getId())
                    .content(networkContent) // Сюда улетает зашифрованный под сессию текст!
                    .timestamp(msg.getTimestamp())
                    .imageUrl(msg.getImageUrl())
                    .status(msg.getStatus())
                    .senderId(msg.getSenderId())
                    .senderName(msg.getSenderName())
                    .recipientId(msg.getRecipientId())
                    .build();

            dtoList.add(dto);
        }

        System.out.println("🟩 [БЭКЕНД] История успешно зашифрована сессионным ключом для '" + requesterUsername + "' и отдается. Размер: " + history.size());

        // 5. Отдаем зашифрованную историю в сеть!
        return ResponseEntity.ok(dtoList);
    }

    @MessageMapping("/chat.typing")
    @Operation(
            summary = " [WebSocket STOMP] Трансляция статуса печати текста",
            description = "Вызывается клиентом автоматически при наборе текста в EditText. Пересылает статус (true/false) в персональный топик собеседника в прямом эфире."
    )
    public void processTyping(@Payload Map<String, Object> payload) {
        // Вытаскиваем ID участников и булево поле из сокет-конверта
        Long senderId = Long.valueOf(payload.get("senderId").toString());
        Long recipientId = Long.valueOf(payload.get("recipientId").toString());
        Boolean isTyping = (Boolean) payload.get("isTyping");

        if (senderId == null || recipientId == null || isTyping == null) return;

        // Пересылаем этот статус напрямую в персональный сокет-топик получателя!
        // Его мобилка поймает этот микро-пакет и красиво зажжет надпись на экране.
        messagingTemplate.convertAndSend("/topic/chat.typing." + recipientId, Map.of(
                "senderId", senderId,
                "isTyping", isTyping
        ));
    }
}
