package com.example.messenger.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "user_devices")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserDevice {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    // Уникальный ID устройства (UUID, генерируется на клиенте)
    @Column(name = "device_id", nullable = false, unique = true)
    private String deviceId;

    // Публичный ключ этого устройства (Base64)
    @Column(name = "public_key", columnDefinition = "TEXT", nullable = false)
    private String publicKey;

    // Когда устройство последний раз выходило на связь
    @Column(name = "last_seen")
    private LocalDateTime lastSeen;
}