package com.example.messenger.repository;

import com.example.messenger.model.PasswordResetCode;
import com.example.messenger.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Optional;

public interface PasswordResetCodeRepository extends JpaRepository<PasswordResetCode, Long> {

    // Ищем самый свежий код для юзера
    Optional<PasswordResetCode> findTopByUserOrderByCreatedAtDesc(User user);

    // Удалить все коды юзера (при успешном сбросе или новом запросе)
    @Modifying
    @Transactional
    void deleteAllByUser(User user);

    // Очистка просроченных кодов (для шедулера)
    @Modifying
    @Transactional
    @Query("DELETE FROM PasswordResetCode p WHERE p.expiresAt < :now")
    int deleteExpired(LocalDateTime now);
}
