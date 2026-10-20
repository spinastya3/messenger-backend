package com.example.messenger.dto;

import lombok.Builder;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@Builder
public class ChatPreviewDto {
    private Long buddyId;           // ID собеседника
    private String buddyName;       // Имя собеседника
    private String lastMessagePreview; // Превью последнего сообщения (текст или "Фото")
    private LocalDateTime lastMessageTime; // Время последнего сообщения
    private int unreadCount;        // Сколько непрочитанных
}