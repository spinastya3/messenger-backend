package com.example.messenger.dto;

import lombok.Data;

@Data
public class SocketMessageRequest {
    private Long senderId;
    private Long recipientId;
    private String encryptedForRecipient;
    private String encryptedForSender;
    private String contentPreview;
    private String imageUrl;
}