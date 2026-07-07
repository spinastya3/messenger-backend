package com.example.messenger.model;

import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDateTime;

@Entity
@Table(name = "messages") // Создаем таблицу сообщений
@Data
public class Message {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Связываем сообщение с конкретным отправителем из таблицы users по ID
    @ManyToOne
    @JoinColumn(name = "sender_id", nullable = false)
    private User sender;

    // Связываем сообщение с конкретным получателем из таблицы users по ID
    @ManyToOne
    @JoinColumn(name = "recipient_id", nullable = false)
    private User recipient;
    private String content;  // Текст сообщения
    private LocalDateTime timestamp; // Дата и время
}
