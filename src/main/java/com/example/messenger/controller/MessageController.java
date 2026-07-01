package com.example.messenger.controller;

import com.example.messenger.repository.MessageRepository;
import com.example.messenger.model.MessageDto;
import org.springframework.http.ResponseEntity;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Controller;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.time.LocalDateTime;
import java.util.List;


@Controller
@RequiredArgsConstructor
public class MessageController {

    private final SimpMessagingTemplate messagingTemplate; // Пересылает сообщения
    private final MessageRepository messageRepository; // Где хранятся сообщения в БД

    // Сюда браузер будет присылать сообщения
    @MessageMapping("/chat.send")
    public void processMessage(@Payload MessageDto message) {

        // Записываем время на сервере в сообщение
        message.setTimestamp(LocalDateTime.now());

        // Сохраняем сообщение в БД
        MessageDto savedMessage = messageRepository.save(message);

        // Пересылаем сообщения из БД в чат
        messagingTemplate.convertAndSend("/topic/messages", savedMessage);
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