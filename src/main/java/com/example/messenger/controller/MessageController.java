package com.example.messenger.controller;

import com.example.messenger.PushNotificationService;
import com.example.messenger.model.User;
import com.example.messenger.repository.MessageRepository;
import com.example.messenger.model.Message;
import com.example.messenger.repository.UserRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;


@RestController
@RequiredArgsConstructor
public class MessageController {

    private final SimpMessagingTemplate messagingTemplate; // Пересылает сообщения
    private final MessageRepository messageRepository; // Где хранятся сообщения в БД
    private final UserRepository userRepository; // Пользователи
    private final PushNotificationService pushNotificationService; // Пуши

    // Сюда браузер будет присылать сообщения
    @MessageMapping("/chat.send")
    public void processMessage(@Payload Message message) {

        // Записываем время на сервере в сообщение
        message.setTimestamp(LocalDateTime.now());

        // Сохраняем сообщение в БД
        Message savedMessage = messageRepository.save(message);

        // Пересылаем сообщения из БД в чат
        messagingTemplate.convertAndSend("/topic/messages", savedMessage);

        // Шлём пуш-уведомление
        try {
            // Достаем объект получателя прямо из сообщения
            User recipient = message.getRecipient();

            if (recipient != null) {
                // Ищем этого пользователя в базе, чтобы вытащить его СВЕЖИЙ сохраненный токен!
                Optional<User> recipientFromDb = userRepository.findById(recipient.getId());

                // Проверяем что получатель есть в БД и у него есть токен пуша
                if (recipientFromDb.isPresent() && recipientFromDb.get().getFcmToken() != null) {

                    // Сохраняем токен пуша
                    String targetToken = recipientFromDb.get().getFcmToken();

                    String senderName = "Пользователь";
                    if (message.getSender() != null && message.getSender().getId() != null) {
                        Optional<User> senderFromDb = userRepository.findById(message.getSender().getId());
                        if (senderFromDb.isPresent()) {
                            senderName = senderFromDb.get().getUsername(); // 🟢 Достали реальное имя!
                        }
                    }
                    // Формируем текст уведомления сверху экрана
                    // Напишем сразу имя отправителя в заголовок шторки, чтобы было красиво!
                    String title = senderName;
                    String body = message.getContent(); // текст сообщения в пуше

                    // 🚀 ОТПРАВЛЯЕМ ПУШ: теперь тут железно подставлена переменная senderName вместо ленивого null!
                    pushNotificationService.sendPushNotification(
                            targetToken,
                            title,
                            body,
                            message.getSender().getId(),
                            senderName
                    );
                }
            }
        } catch (Exception e) {
            System.err.println("🟨 Не удалось отправить пуш-уведомление: " + e.getMessage());
        }
    }

    @GetMapping("/api/chat/history")
    public ResponseEntity<List<Message>> getChatHistory(@RequestParam Long senderId, @RequestParam Long recipientId) {

        // Запрашиваем у репозитория всю переписку между этими двумя пользователями
        List<Message> history = messageRepository.findBySenderIdAndRecipientIdOrSenderIdAndRecipientIdOrderByTimestampAsc(
                senderId, recipientId, recipientId, senderId
        );
        return ResponseEntity.ok(history);
    }
}