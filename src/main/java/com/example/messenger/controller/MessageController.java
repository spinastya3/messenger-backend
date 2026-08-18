package com.example.messenger.controller;

import com.example.messenger.PushNotificationService;
import com.example.messenger.model.Message;
import com.example.messenger.model.MessageStatus;
import com.example.messenger.model.User;
import com.example.messenger.repository.MessageRepository;
import com.example.messenger.repository.UserRepository;
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

    // 1. Сюда приходят новые сообщения от отправителя
    @MessageMapping("/chat.send")
    public void processMessage(@Payload Message message) {

        System.out.println("🔍 [ДО СОХРАНЕНИЯ] Ссылка от мобилки: " + message.getImageUrl());


        // Записываем время на сервере в сообщение
        message.setTimestamp(LocalDateTime.now());

        // Ставим статус SENT в БД для нового сообщения
        message.setStatus(MessageStatus.SENT);

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

    // 2. ДОБАВИЛИ: Сюда Android шлет сигнал, когда его устройство приняло сообщение (в фоне или в чате)
    @MessageMapping("/chat.delivered")
    @Operation(
            summary = " [WebSocket STOMP] Отметить сообщения от собеседника как доставленные",
            description = "Вызывается клиентом автоматически при получении сообщения. Переводит сообщения в статус DELIVERED и отправляет событие-оповещение отправителю."
    )
    public void deliveredMessages(@Payload Map<String, Long> payload) {
        Long senderId = payload.get("senderId"); // Тот, кто нам отправил
        Long recipientId = payload.get("recipientId"); // Мы (получатель)

        if (senderId == null || recipientId == null) return;

        // Берем из твоего репозитория сообщения со статусом SENT
        List<Message> sentMessages = messageRepository.findSentMessages(senderId, recipientId);

        if (!sentMessages.isEmpty()) {
            for (Message msg : sentMessages) {
                msg.setStatus(MessageStatus.DELIVERED);
            }
            messageRepository.saveAll(sentMessages);

            // Оповещаем ОТПРАВИТЕЛЯ в его персональный топик статусов
            messagingTemplate.convertAndSend("/topic/messages.status." + senderId, Map.of(
                    "status", "DELIVERED",
                    "recipientId", recipientId,
                    "senderId", senderId
            ));

            System.out.println("📩 Сообщения от " + senderId + " доставлены пользователю " + recipientId + ". Статус: DELIVERED");
        }
    }

    // 3. Сюда Android шлет сигнал, когда пользователь открыл экран чата
    @MessageMapping("/chat.read")
    @Operation(
            summary = " [WebSocket STOMP] Отметить сообщения от собеседника как прочитанные",
            description = "Вызывается клиентом через WebSocket при открытии чата. Массово переводит входящие сообщения от указанного отправителя в статус READ и отправляет событие-оповещение в топик статусов отправителя."
    )
    public void readMessages(@Payload Map<String, Long> payload) {
        Long senderId = payload.get("senderId"); // Тот, чей чат мы открыли
        Long recipientId = payload.get("recipientId"); // Мы (кто прочитал)

        if (senderId == null || recipientId == null) return;

        // Находим все непрочитанные (и SENT, и DELIVERED через твой обновленный метод)
        List<Message> unreadMessages = messageRepository.findUnreadMessages(senderId, recipientId);

        if (!unreadMessages.isEmpty()) {
            for (Message msg : unreadMessages) {
                msg.setStatus(MessageStatus.READ);
            }
            messageRepository.saveAll(unreadMessages);

            // Оповещаем ОТПРАВИТЕЛЯ в тот же топик статусов
            messagingTemplate.convertAndSend("/topic/messages.status." + senderId, Map.of(
                    "status", "READ",
                    "recipientId", recipientId,
                    "senderId", senderId
            ));

            System.out.println("👥 Пользователь " + recipientId + " прочитал сообщения от " + senderId + ". Статус: READ");
        }
    }

    @GetMapping("/api/chat/history")
    public ResponseEntity<?> getChatHistory(@RequestParam Long senderId, @RequestParam Long recipientId) {
        if (senderId == null || senderId <= 0 || recipientId == null || recipientId <= 0) {
            return ResponseEntity
                    .badRequest()
                    .body("Ошибка 400: Некорректные ID пользователей");
        }
        List<Message> history = messageRepository.findChatHistory(senderId, recipientId);
        return ResponseEntity.ok(history);
    }
}





//package com.example.messenger.controller;
//
//import com.example.messenger.PushNotificationService;
//import com.example.messenger.model.MessageStatus;
//import com.example.messenger.model.User;
//import com.example.messenger.repository.MessageRepository;
//import com.example.messenger.model.Message;
//import com.example.messenger.repository.UserRepository;
//import io.swagger.v3.oas.annotations.Operation;
//import io.swagger.v3.oas.annotations.responses.ApiResponse;
//import io.swagger.v3.oas.annotations.responses.ApiResponses;
//import org.springframework.http.ResponseEntity;
//import org.springframework.messaging.handler.annotation.MessageMapping;
//import org.springframework.messaging.handler.annotation.Payload;
//import org.springframework.messaging.simp.SimpMessagingTemplate;
//import lombok.RequiredArgsConstructor;
//import org.springframework.web.bind.annotation.CrossOrigin;
//import org.springframework.web.bind.annotation.GetMapping;
//import org.springframework.web.bind.annotation.RequestParam;
//import org.springframework.web.bind.annotation.RestController;
//
//import java.time.LocalDateTime;
//import java.util.List;
//import java.util.Map;
//import java.util.Optional;
//
//
//@RestController
//@RequiredArgsConstructor
//@CrossOrigin(origins = "*", allowedHeaders = "*")
//public class MessageController {
//
//    private final SimpMessagingTemplate messagingTemplate; // Пересылает сообщения
//    private final MessageRepository messageRepository; // Где хранятся сообщения в БД
//    private final UserRepository userRepository; // Пользователи
//    private final PushNotificationService pushNotificationService; // Пуши
//
//    // Сюда приходят сообщения
//    @MessageMapping("/chat.send")
//    public void processMessage(@Payload Message message) {
//
//        // Записываем время на сервере в сообщение
//        message.setTimestamp(LocalDateTime.now());
//
//        // Ставим статус SENT в БД для нового сообщения
//        message.setStatus(MessageStatus.SENT);
//
//        // Сохраняем сообщение в БД
//        Message savedMessage = messageRepository.save(message);
//
//        if (savedMessage.getRecipient() != null && savedMessage.getRecipient().getId() != null) {
//            messagingTemplate.convertAndSend("/topic/messages." + savedMessage.getRecipient().getId(), savedMessage);
//        }
//
//        if (savedMessage.getSender() != null && savedMessage.getSender().getId() != null) {
//            messagingTemplate.convertAndSend("/topic/messages." + savedMessage.getSender().getId(), savedMessage);
//        }
//
//        // Шлём пуш-уведомление
//        try {
//            // Достаем объект получателя прямо из сообщения
//            User recipient = message.getRecipient();
//
//            if (recipient != null) {
//                // Ищем этого пользователя в базе, чтобы вытащить его сохраненный токен
//                Optional<User> recipientFromDb = userRepository.findById(recipient.getId());
//
//                // Проверяем что получатель есть в БД и у него есть токен пуша
//                if (recipientFromDb.isPresent() && recipientFromDb.get().getFcmToken() != null) {
//
//                    // Сохраняем токен пуша
//                    String targetToken = recipientFromDb.get().getFcmToken();
//
//                    String senderName = "Пользователь";
//                    if (message.getSender() != null && message.getSender().getId() != null) {
//                        Optional<User> senderFromDb = userRepository.findById(message.getSender().getId());
//                        if (senderFromDb.isPresent()) {
//                            senderName = senderFromDb.get().getUsername();
//                        }
//
//                        // Формируем текст уведомления сверху экрана
//                        String title = senderName;
//                        String body = message.getContent(); // текст сообщения в пуше
//
//                        if (message.getImageUrl() != null && !message.getImageUrl().isEmpty()) {
//                            if (body == null || body.trim().isEmpty()) {
//                                body = "Фотография";
//                            }
//                        }
//
//                        // Отправляем пуш
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
//                System.err.println("🟨 Не удалось отправить пуш-уведомление: " + e.getMessage());
//            }
//    }
//
//    @MessageMapping("/chat.read")
//    @Operation(
//            summary = " [WebSocket STOMP] Отметить сообщения от собеседника как прочитанные",
//            description = "Вызывается клиентом через WebSocket при открытии чата. Массово переводит входящие сообщения от указанного отправителя в статус READ и отправляет событие-оповещение в топик /topic/messages.read.{senderId}"
//    )
//    public void readMessages(@Payload Map<String, Long> payload) {
//        Long senderId = payload.get("senderId");
//        Long recipientId = payload.get("recipientId");
//
//        if (senderId == null || recipientId == null) return;
//
//        // Находим в БД все сообщения, которые отправил этот собеседник и которые еще не прочитаны
//        List<Message> unreadMessages = messageRepository.findUnreadMessages(senderId, recipientId);
//
//        if (!unreadMessages.isEmpty()) {
//            // Массово переводим их в статус READ
//            for (Message msg : unreadMessages) {
//                msg.setStatus(MessageStatus.READ);
//            }
//            // Сохраняем пачку обновленных сообщений в БД
//            messageRepository.saveAll(unreadMessages);
//
//            // Шлем в канал отправителя список ID сообщений, которые стали прочитанными
//            messagingTemplate.convertAndSend("/topic/messages.read." + senderId, Map.of(
//                    "status", "READ",
//                    "readerId", recipientId,
//                    "senderId", senderId
//            ));
//
//            System.out.println("👥 Пользователь " + recipientId + " прочитал сообщения от " + senderId + ". Статус обновлен!");
//        }
//    }
//
//
//    @Operation(
//            summary = "Получить историю переписки между двумя пользователями",
//            description = "Скачивает из базы данных Postgres полный архив сообщений между отправителем и получателем, отсортированный по времени от старых к новым."
//    )
//    @ApiResponses(value = {
//            @ApiResponse(responseCode = "200", description = "История чата успешно загружена"),
//            @ApiResponse(responseCode = "400", description = "Некорректный запрос: ID отправителя или получателя пустой, равен 0 или отрицательный")
//    })
//
//    @GetMapping("/api/chat/history")
//    public ResponseEntity<?> getChatHistory(@RequestParam Long senderId, @RequestParam Long recipientId) {
//
//        if (senderId == null || senderId <= 0 || recipientId == null || recipientId <= 0) {
//            return ResponseEntity
//                    .badRequest()
//                    .body("Ошибка 400: Некорректные ID пользователей");
//        }
//        List<Message> history = messageRepository.findChatHistory(senderId, recipientId);
//        return ResponseEntity.ok(history);
//    }
//}