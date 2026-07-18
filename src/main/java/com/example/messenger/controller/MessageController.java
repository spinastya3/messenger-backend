package com.example.messenger.controller;

import com.example.messenger.PushNotificationService;
import com.example.messenger.model.User;
import com.example.messenger.repository.MessageRepository;
import com.example.messenger.model.Message;
import com.example.messenger.repository.UserRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import org.springframework.http.ResponseEntity;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;


@RestController
@RequiredArgsConstructor
@CrossOrigin(origins = "*", allowedHeaders = "*")
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

        if (savedMessage.getRecipient() != null && savedMessage.getRecipient().getId() != null) {
            messagingTemplate.convertAndSend("/topic/messages." + savedMessage.getRecipient().getId(), savedMessage);
        }

        if (savedMessage.getSender() != null && savedMessage.getSender().getId() != null) {
            messagingTemplate.convertAndSend("/topic/messages." + savedMessage.getSender().getId(), savedMessage);
        }

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

                    if (message.getImageUrl() != null && !message.getImageUrl().isEmpty()) {
                        if (body == null || body.trim().isEmpty()) {
                            body = "Фотография";
                        }
                    }

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

    @Operation(
            summary = "Получить историю переписки между двумя пользователями",
            description = "Скачивает из базы данных Postgres полный архив сообщений между отправителем и получателем, отсортированный по времени от старых к новым."
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "История чата успешно загружена"),
            @ApiResponse(responseCode = "400", description = "Некорректный запрос: ID отправителя или получателя пустой, равен 0 или отрицательный")
    })

    @GetMapping("/api/chat/history")
    public ResponseEntity<?> getChatHistory(@RequestParam Long senderId, @RequestParam Long recipientId) {

        if (senderId == null || senderId <= 0 || recipientId == null || recipientId <= 0) {
            return ResponseEntity
                    .badRequest()
                    .body("Ошибка 400: Некорректные ID пользователей");
        }
        // Запрашиваем у репозитория всю переписку между этими двумя пользователями
        List<Message> history = messageRepository.findChatHistory(senderId, recipientId);
        return ResponseEntity.ok(history);
    }
}