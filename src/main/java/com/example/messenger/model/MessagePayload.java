package com.example.messenger.model;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "message_payloads", indexes = {
        @Index(name = "idx_payload_msg", columnList = "message_id"),
        @Index(name = "idx_payload_recipient", columnList = "recipient_id")
}, uniqueConstraints = {
        @UniqueConstraint(name = "uk_payload_msg_recipient", columnNames = {"message_id", "recipient_id"})
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MessagePayload {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "message_id", nullable = false)
    private Message message;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "recipient_id", nullable = false)
    private User recipient;

    @Column(name = "encrypted_payload", columnDefinition = "TEXT", nullable = false)
    private String encryptedPayload;

    // 🚀 ПЕРЕЕХАЛО СЮДА: статус конкретного получателя
    @Enumerated(EnumType.STRING)
    @Column(name = "status", columnDefinition = "VARCHAR(255) CHECK (status IN ('SENT', 'DELIVERED', 'READ'))")
    private MessageStatus status = MessageStatus.SENT;

    @Column(name = "crypto_version", nullable = false)
    @Builder.Default
    private int cryptoVersion = 1;

    @PrePersist
    void onCreate() {
        if (status == null) status = MessageStatus.SENT;
    }
}