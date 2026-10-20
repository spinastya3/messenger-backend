package com.example.messenger.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "chats")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Chat {

    @Id
    // Если личный чат - задаем ID сами (формула). Если группа - БД выдаст сама.
    private Long id;

    @Column(nullable = false)
    private String type; // "PRIVATE" или "GROUP"

    private String title; // Для групп

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @PrePersist
    void onCreate() {
        if (createdAt == null) createdAt = LocalDateTime.now();
        if (type == null) type = "PRIVATE";
    }
}