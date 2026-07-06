package com.example.messenger.controller;

import com.example.messenger.PushNotificationService;
import com.example.messenger.model.User;
import com.example.messenger.repository.MessageRepository;
import com.example.messenger.model.MessageDto;
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
    public void processMessage(@Payload MessageDto message) {

        // Записываем время на сервере в сообщение
        message.setTimestamp(LocalDateTime.now());

        // Сохраняем сообщение в БД
        MessageDto savedMessage = messageRepository.save(message);

        // Пересылаем сообщения из БД в чат
        messagingTemplate.convertAndSend("/topic/messages", savedMessage);

        // Шлём пуш-уведомление
        try {

            // Достаем объект получателя прямо из сообщения
            User recipient = message.getRecipient();

            if (recipient != null) {
                // Ищем этого пользователя в базе, чтобы вытащить его СВЕЖИЙ сохраненный токен!
                Optional<User> recipientFromDb = userRepository.findById(recipient.getId());

                // Проверяем что отправитель есть в БД и у него есть токен пуша
                if (recipientFromDb.isPresent() && recipientFromDb.get().getFcmToken() != null) {

                    // охраняем токен пуша
                    String targetToken = recipientFromDb.get().getFcmToken();

                    String senderName = "Пользователь";
                    if (message.getSender() != null && message.getSender().getId() != null) {
                        Optional<User> senderFromDb = userRepository.findById(message.getSender().getId());
                        if (senderFromDb.isPresent()) {
                            senderName = senderFromDb.get().getUsername();
                        }
                    }

                    // Формируем текст уведомления сверху экрана
                    String title = "Новое сообщение";
                    String body = message.getContent(); // текст сообщения в пуше

                    // Отправляем пуш
                    pushNotificationService.sendPushNotification(targetToken, title, body);
                }
            }
        } catch (Exception e) {
            System.err.println("🟨 Не удалось отправить пуш-уведомление: " + e.getMessage());
        }
    }



    @GetMapping("/api/chat/history")
    public ResponseEntity<List<MessageDto>> getChatHistory(@RequestParam Long senderId, @RequestParam Long recipientId) {

        // Запрашиваем у репозитория всю переписку между этими двумя пользователями
        List<MessageDto> history = messageRepository.findBySenderIdAndRecipientIdOrSenderIdAndRecipientIdOrderByTimestampAsc(
                senderId, recipientId, recipientId, senderId
        );
        return ResponseEntity.ok(history);
    }
}