package com.example.messenger.dto;

import com.example.messenger.model.MessageStatus;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MessageDto {

    private Long id;
    private String content;
    private LocalDateTime timestamp;
    private String imageUrl;
    private MessageStatus status;

    // Джексон превратит их в "senderId", "senderName" и "recipientId" на верхнем уровне JSON, как и было в Entity
    @JsonProperty("senderId")
    private Long senderId;

    @JsonProperty("senderName")
    private String senderName;

    @JsonProperty("recipientId")
    private Long recipientId;
}
