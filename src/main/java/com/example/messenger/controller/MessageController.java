package com.example.messenger.controller;

import com.example.messenger.dto.MessageDto;
import com.example.messenger.dto.SendMessageRequest;
import com.example.messenger.model.Message;
import com.example.messenger.model.MessagePayload;
import com.example.messenger.model.MessageStatus;
import com.example.messenger.model.User;
import com.example.messenger.repository.MessagePayloadRepository;
import com.example.messenger.repository.MessageRepository;
import com.example.messenger.repository.UserRepository;
import com.example.messenger.service.MessageService;
import com.example.messenger.service.PushNotificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.util.List;
import java.util.Map;

@RestController
@RequiredArgsConstructor
@CrossOrigin(origins = "*", allowedHeaders = "*")
public class MessageController {

    private final SimpMessagingTemplate messagingTemplate;
    private final MessageRepository messageRepository;
    private final MessagePayloadRepository payloadRepository;
    private final UserRepository userRepository;
    private final PushNotificationService pushNotificationService;
    private final MessageService messageService;

    @MessageMapping("/chat.send")
    public void processMessage(@Payload SendMessageRequest req, Principal principal) {
        if (principal == null) {
            System.err.println("🟥 [WS] Нет авторизации!");
            return;
        }

        User sender = userRepository.findByUsername(principal.getName())
                .orElseThrow(() -> new IllegalArgumentException("Отправитель не найден"));

        req.setSenderId(sender.getId());

        try {
            Message savedMessage = messageService.sendMessage(sender.getId(), req);

// 🚀 Получаем payload для отправителя и получателя
            List<MessagePayload> payloads = savedMessage.getPayloads();

            if (payloads != null && !payloads.isEmpty()) {
                for (MessagePayload payload : payloads) {
                    // Создаем DTO для сокета (с шифротекстом и ID)
                    MessageDto socketDto = MessageDto.builder()
                            .id(savedMessage.getId())
                            .senderId(savedMessage.getSenderId())
                            .senderName(savedMessage.getSenderName())
                            .recipientId(savedMessage.getRecipientId())
                            .content(payload.getEncryptedPayload()) // 🚀 Вот он, шифротекст!
                            .timestamp(savedMessage.getTimestamp())
                            .imageUrl(savedMessage.getImageUrl())
                            .status(payload.getStatus())
                            .build();

                    // Отправляем в личный топик получателя этого payload
                    if (payload.getRecipient() != null) {
                        messagingTemplate.convertAndSend("/topic/messages." + payload.getRecipient().getId(), socketDto);
                    }
                }
            }

            // Пуш
            User recipient = savedMessage.getRecipient();
            if (recipient != null && recipient.getFcmToken() != null) {
                String title = sender.getUsername();
                String body = req.getContentPreview() != null ? req.getContentPreview() : "Новое сообщение";
                if (req.getImageUrl() != null && !req.getImageUrl().isEmpty()) body = "Фотография";

                pushNotificationService.sendPushNotification(
                        recipient.getFcmToken(), title, body, sender.getId(), title
                );
            }
        } catch (Exception e) {
            System.err.println("🟥 Ошибка отправки: " + e.getMessage());
            e.printStackTrace();
        }
    }

    @PostMapping("/api/chat/status/delivered")
    public ResponseEntity<Void> markAsDeliveredHttp(
            @RequestParam Long senderId,
            @RequestParam Long recipientId,
            Principal principal) {

        if (principal == null) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        User currentUser = userRepository.findByUsername(principal.getName()).orElseThrow();
        if (!currentUser.getId().equals(recipientId)) return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        if (senderId == null || senderId <= 0) return ResponseEntity.badRequest().build();

        List<MessagePayload> sentPayloads = payloadRepository.findSentPayloads(senderId, recipientId);

        if (!sentPayloads.isEmpty()) {
            for (MessagePayload p : sentPayloads) p.setStatus(MessageStatus.DELIVERED);
            payloadRepository.saveAll(sentPayloads);

            messagingTemplate.convertAndSend("/topic/messages.status." + senderId, Map.of(
                    "status", "DELIVERED", "recipientId", recipientId, "senderId", senderId
            ));
        }
        return ResponseEntity.ok().build();
    }

    @MessageMapping("/chat.read")
    public void readMessages(@Payload Map<String, Long> payload, Principal principal) {
        if (principal == null) return;
        Long senderId = payload.get("senderId");
        Long recipientId = payload.get("recipientId");

        User currentUser = userRepository.findByUsername(principal.getName()).orElseThrow();
        if (!currentUser.getId().equals(recipientId)) return;

        List<MessagePayload> unreadPayloads = payloadRepository.findUnreadPayloads(senderId, recipientId);

        if (!unreadPayloads.isEmpty()) {
            for (MessagePayload p : unreadPayloads) p.setStatus(MessageStatus.READ);
            payloadRepository.saveAll(unreadPayloads);
        }

        messagingTemplate.convertAndSend("/topic/messages.status." + senderId, Map.of(
                "status", "READ", "recipientId", recipientId, "senderId", senderId
        ));

        System.out.println("📥 [READ] Пришел от " + principal.getName());
    }

    @GetMapping("/api/chat/history")
    public ResponseEntity<?> getChatHistory(
            @RequestParam Long senderId,
            @RequestParam Long recipientId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            Principal principal) {

        if (principal == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Не авторизован");
        }

        User currentUser = userRepository.findByUsername(principal.getName()).orElseThrow();

        // 🚀 Сервис сам соберет DTO!
        List<MessageDto> history = messageService.getChatHistory(currentUser.getId(), recipientId, page, size);

        return ResponseEntity.ok(history);
    }

    @MessageMapping("/chat.typing")
    public void processTyping(@Payload Map<String, Object> payload, Principal principal) {
        if (principal == null) return;
        Long recipientId = Long.valueOf(payload.get("recipientId").toString());
        Boolean isTyping = (Boolean) payload.get("isTyping");
        User currentUser = userRepository.findByUsername(principal.getName()).orElseThrow();

        messagingTemplate.convertAndSend("/topic/chat.typing." + recipientId, Map.of(
                "senderId", currentUser.getId(), "isTyping", isTyping
        ));
    }
}