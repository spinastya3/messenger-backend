package com.example.messenger.controller;

import com.example.messenger.dto.MessageDto;
import com.example.messenger.service.MessageService;
import com.example.messenger.service.PushNotificationService;
import com.example.messenger.model.Message;
import com.example.messenger.model.MessageStatus;
import com.example.messenger.model.User;
import com.example.messenger.repository.MessageRepository;
import com.example.messenger.repository.UserRepository;
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

    private final SimpMessagingTemplate messagingTemplate;
    private final MessageRepository messageRepository;
    private final UserRepository userRepository;
    private final PushNotificationService pushNotificationService;
    private final MessageService messageService;

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

        messageRepository.save(message);

        if (message.getRecipient() != null && message.getRecipient().getId() != null) {
            messagingTemplate.convertAndSend("/topic/messages." + message.getRecipient().getId(), message);
        }

        if (message.getSender() != null && message.getSender().getId() != null) {
            messagingTemplate.convertAndSend("/topic/messages." + message.getSender().getId(), message);
        }

        System.out.println("🔍 [ПОСЛЕ СОХРАНЕНИЯ] Ссылка из БД: " + message.getImageUrl());

        // 🚀 ЛОГИКА ПУШЕЙ (Без текста сообщения, так как сервер его не знает)
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
                        // На этапе E2EE текст сообщения для пуша неизвестен.
                        // Пока используем заглушку. На этапе 3 добавим contentPreview.
                        String body = "Новое сообщение";

                        // Если это медиафайл (без текста), но с картинкой
                        if (message.getContent() == null && message.getImageUrl() != null && !message.getImageUrl().isEmpty()) {
                            body = "Фотография";
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
    @Operation(summary = "Отметить сообщения как доставленные")
    public ResponseEntity<Void> markAsDeliveredHttp(
            @RequestParam Long senderId,
            @RequestParam Long recipientId) {

        if (senderId == null || senderId <= 0 || recipientId == null || recipientId <= 0) {
            return ResponseEntity.badRequest().build();
        }

        System.out.println("📥 [БЭКЕНД] Фоновый HTTP запрос доставки. Отправитель: " + senderId + " | Получатель (мы): " + recipientId);

        List<Message> sentMessages = messageRepository.findSentMessages(senderId, recipientId);

        if (!sentMessages.isEmpty()) {
            for (Message msg : sentMessages) {
                msg.setStatus(MessageStatus.DELIVERED);
            }
            messageRepository.saveAll(sentMessages);

            messagingTemplate.convertAndSend("/topic/messages.status." + senderId, Map.of(
                    "status", "DELIVERED",
                    "recipientId", recipientId,
                    "senderId", senderId
            ));
        }
        return ResponseEntity.ok().build();
    }

    @MessageMapping("/chat.read")
    public void readMessages(@Payload Map<String, Long> payload) {
        Long senderId = payload.get("senderId");
        Long recipientId = payload.get("recipientId");

        if (senderId == null || recipientId == null) return;

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
            Principal principal) {

        if (senderId == null || senderId <= 0 || recipientId == null || recipientId <= 0) {
            return ResponseEntity.badRequest().body("Ошибка 400: Некорректные ID пользователей");
        }

        if (principal == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Пользователь не авторизован");
        }

        System.out.println("📥 [БЭКЕНД] HTTP запрос истории. Собеседники: " + senderId + " и " + recipientId + " | Страница: " + page);

        // Получаем историю из базы (она уже хранится в виде шифротекста)
        List<Message> history = messageService.getChatHistory(senderId, recipientId, page, size);

        List<MessageDto> dtoList = new ArrayList<>();

        for (Message msg : history) {
            // 🚀 ГЛАВНОЕ ИЗМЕНЕНИЕ: Отдаем шифротекст КАК ЕСТЬ, без шифрования на сервере
            MessageDto dto = MessageDto.builder()
                    .id(msg.getId())
                    .content(msg.getContent()) // Шифротекст от отправителя
                    .timestamp(msg.getTimestamp())
                    .imageUrl(msg.getImageUrl())
                    .status(msg.getStatus())
                    .senderId(msg.getSenderId())
                    .senderName(msg.getSenderName())
                    .recipientId(msg.getRecipientId())
                    .build();

            dtoList.add(dto);
        }

        System.out.println("🟩 [БЭКЕНД] История отдана как есть. Размер: " + history.size());
        return ResponseEntity.ok(dtoList);
    }

    @MessageMapping("/chat.typing")
    public void processTyping(@Payload Map<String, Object> payload) {
        Long senderId = Long.valueOf(payload.get("senderId").toString());
        Long recipientId = Long.valueOf(payload.get("recipientId").toString());
        Boolean isTyping = (Boolean) payload.get("isTyping");

        if (senderId == null || recipientId == null || isTyping == null) return;

        messagingTemplate.convertAndSend("/topic/chat.typing." + recipientId, Map.of(
                "senderId", senderId,
                "isTyping", isTyping
        ));
    }
}
