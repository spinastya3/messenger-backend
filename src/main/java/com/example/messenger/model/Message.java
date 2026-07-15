package com.example.messenger.model; // 🟢 Твой бэкенд-пакет!

import com.fasterxml.jackson.annotation.JsonProperty; // 🔥 НАШ КЛЮЧЕВОЙ ИМПОРТ!
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

    @ManyToOne
    @JoinColumn(name = "sender_id", nullable = false)
    private User sender;

    @ManyToOne
    @JoinColumn(name = "recipient_id", nullable = false)
    private User recipient;

    private String content;
    private LocalDateTime timestamp;
    private String imageUrl;

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
