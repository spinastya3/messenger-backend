package com.example.messenger.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDateTime;

@Entity
@Table(name = "messages")
@Data
public class Message {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "sender_id", nullable = false)
    private User sender;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "recipient_id", nullable = false)
    private User recipient;

    private String content;
    private LocalDateTime timestamp;
    private String imageUrl;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private MessageStatus status = MessageStatus.SENT;

    // Эти методы Джексон автоматически превратит в ключи "senderId" и "senderName" на верхнем уровне JSON!
    @JsonProperty("senderId")
    public Long getSenderId() {
        return sender != null ? sender.getId() : null;
    }

    @JsonProperty("senderName")
    public String getSenderName() {
        return sender != null ? sender.getUsername() : "Пользователь";
    }

    @JsonProperty("recipientId")
    public Long getRecipientId() {
        return recipient != null ? recipient.getId() : null;
    }
}
