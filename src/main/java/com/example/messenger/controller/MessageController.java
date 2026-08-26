package com.example.messenger.controller;

import com.example.messenger.service.MessageService;
import com.example.messenger.service.PushNotificationService;
import com.example.messenger.model.Message;
import com.example.messenger.model.MessageStatus;
import com.example.messenger.model.User;
import com.example.messenger.repository.MessageRepository;
import com.example.messenger.repository.UserRepository;
import com.example.messenger.util.EncryptionUtil;
import io.swagger.v3.oas.annotations.Operation;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
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


    // 1. Сюда приходят новые сообщения от отправителя
    @MessageMapping("/chat.send")
    public void processMessage(@Payload Message message) {

        System.out.println("🔍 [ДО СОХРАНЕНИЯ] Ссылка от мобилки: " + message.getImageUrl());

        // Записываем время на сервере в сообщение
        message.setTimestamp(LocalDateTime.now());

        // Ставим статус SENT в БД для нового сообщения
        message.setStatus(MessageStatus.SENT);

        if (message.getContent() != null) {
            String encryptedText = encryptionUtil.encrypt(message.getContent());
            message.setContent(encryptedText);
        }

        // Сохраняем сообщение в БД
        Message savedMessage = messageRepository.save(message);

        System.out.println("🔍 [ПОСЛЕ СОХРАНЕНИЯ] Ссылка из БД: " + savedMessage.getImageUrl());

        // Шлем получателю (он поймает его и сразу ответит серверу, что оно доставлено)
        if (savedMessage.getRecipient() != null && savedMessage.getRecipient().getId() != null) {
            messagingTemplate.convertAndSend("/topic/messages." + savedMessage.getRecipient().getId(), savedMessage);
        }

        // Шлем обратно отправителю (чтобы на экране появилась первая галочка)
        if (savedMessage.getSender() != null && savedMessage.getSender().getId() != null) {
            messagingTemplate.convertAndSend("/topic/messages." + savedMessage.getSender().getId(), savedMessage);
        }

        if (savedMessage.getContent() != null) {
            String decryptedText = encryptionUtil.decrypt(savedMessage.getContent());
            savedMessage.setContent(decryptedText);
        }

        // Шлём пуш-уведомление
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
                        String body = message.getContent();

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

//    @GetMapping("/api/chat/history")
//    public ResponseEntity<?> getChatHistory(@RequestParam Long senderId, @RequestParam Long recipientId) {
//        if (senderId == null || senderId <= 0 || recipientId == null || recipientId <= 0) {
//            return ResponseEntity
//                    .badRequest()
//                    .body("Ошибка 400: Некорректные ID пользователей");
//        }
//        List<Message> history = messageRepository.findChatHistory(senderId, recipientId);
//        return ResponseEntity.ok(history);
//    }

    @GetMapping("/api/chat/history")
    public ResponseEntity<?> getChatHistory(
            @RequestParam Long senderId,
            @RequestParam Long recipientId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        if (senderId == null || senderId <= 0 || recipientId == null || recipientId <= 0) {
            return ResponseEntity
                    .badRequest()
                    .body("Ошибка 400: Некорректные ID пользователей");
        }

        System.out.println("📥 [БЭКЕНД] HTTP запрос истории. Собеседники: " + senderId + " и " + recipientId + " | Страница: " + page);

        List<Message> history = messageService.getChatHistory(senderId, recipientId, page, size);

        System.out.println("🟩 [БЭКЕНД] Успешно отдаем порцию истории. Размер: " + history.size());

        return ResponseEntity.ok(history);
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
