package com.example.messenger.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "messages", indexes = {
        @Index(name = "idx_msg_chat_created", columnList = "chat_id, timestamp")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Message {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "chat_id", nullable = false)
    private Long chatId;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "sender_id", nullable = false)
    private User sender;

    // Оставляем для удобства пушей и старого клиента
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "recipient_id", nullable = false)
    private User recipient;

    @Column(name = "content_preview")
    private String contentPreview;

    @Column(name = "image_url")
    private String imageUrl;

    @Column(name = "timestamp", nullable = false)
    private LocalDateTime timestamp;

    @OneToMany(mappedBy = "message", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @Builder.Default
    private List<MessagePayload> payloads = new ArrayList<>();

    @PrePersist
    void onCreate() {
        if (timestamp == null) timestamp = LocalDateTime.now();
    }

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