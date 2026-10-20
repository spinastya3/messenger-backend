package com.example.messenger.dto;

import lombok.Data;

@Data
public class SendMessageRequest {
    private Long senderId;                // Кто отправляет (проверяется на сервере)
    private Long recipientId;             // Кому шлем
    private String encryptedForRecipient; // Шифротекст для получателя
    private String encryptedForSender;    // Шифротекст для себя
    private String contentPreview;        // Текст для пуша
    private String imageUrl;              // Если есть картинка
}