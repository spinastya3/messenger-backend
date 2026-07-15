package com.example.messenger.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.List;

@Entity
@Table(name = "app_users") // Создаем таблицу пользователей
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class User implements UserDetails {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id; // ID пользователя он же первичный ключ

    @Column(unique = true, nullable = false)
    private String username; // Имя

    @Column(nullable = false)
    private String password; // Пароль

    @Column(unique = true)
    private String email; // Email

    private String fcmToken; // Токен дла пуша

    @Override //  всех пользователей одна доступная роль
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of(() -> "ROLE_USER");
    }

    // Пароли не надо менять, блокировать, поддтверждать
    @Override public boolean isAccountNonExpired() { return true; }
    @Override public boolean isAccountNonLocked() { return true; }
    @Override public boolean isCredentialsNonExpired() { return true; }
    @Override public boolean isEnabled() { return true; }
}
