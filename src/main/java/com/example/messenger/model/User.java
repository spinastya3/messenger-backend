package com.example.messenger.model;

import jakarta.persistence.*;
import lombok.Data;

@Entity
@Table(name = "users") // Создаем таблицу пользователей
@Data
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)

    private Long id; // ID он же первичный ключ
    private String username; // Имя
}
