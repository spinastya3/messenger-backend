package com.example.messenger.service;

import com.example.messenger.model.PasswordResetCode;
import com.example.messenger.model.User;
import com.example.messenger.repository.PasswordResetCodeRepository;
import com.example.messenger.repository.UserRepository;
import com.example.messenger.util.JwtUtil;
import jakarta.transaction.Transactional;
import org.springframework.http.ResponseEntity;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;

import java.time.LocalDateTime;
import java.util.*;

@Service
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final PasswordResetCodeRepository resetCodeRepository;
    private final JwtUtil jwtUtil;
    private final EmailService emailService;

    public AuthService(UserRepository userRepository,
                       PasswordEncoder passwordEncoder,
                       JwtUtil jwtUtil,
                       EmailService emailService,
                       PasswordResetCodeRepository resetCodeRepository) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtUtil = jwtUtil;
        this.emailService = emailService;
        this.resetCodeRepository = resetCodeRepository;
    }

    // Регистрируем пользователя
    public Map<String, String> register(String username, String rawPassword, String email, String fcmToken, String clientPublicKey) {

        Optional.ofNullable(username)
                .filter(u -> !u.trim().isEmpty())
                .orElseThrow(() -> new IllegalArgumentException("Введите логин!"));

        userRepository.findByUsername(username).ifPresent(user -> {
            throw new IllegalArgumentException("Пользователь с таким логином уже существует!");
        });

        String emailPattern = "^[a-zA-Z0-9_!#$%&'*+/=?`{|}~^-]+(?:\\.[a-zA-Z0-9_!#$%&'*+/=?`{|}~^-]+)*@[a-zA-Z0-9-]+(?:\\.[a-zA-Z0-9-]+)*$";

        Optional.ofNullable(email)
                .filter(e -> !e.trim().isEmpty())
                .orElseThrow(() -> new IllegalArgumentException("Введите email!"));

        Optional.of(email)
                .filter(e -> e.matches(emailPattern))
                .orElseThrow(() -> new IllegalArgumentException("Введите корректный Email!"));

        Optional.of(email)
                .filter(userRepository::existsByEmail)
                .ifPresent(e -> {
                    throw new IllegalArgumentException("Пользователь с такой почтой уже существует!");
                });

        Optional.ofNullable(rawPassword)
                .filter(p -> !p.trim().isEmpty())
                .orElseThrow(() -> new IllegalArgumentException("Введите пароль!"));

        Optional.ofNullable(clientPublicKey)
                .filter(k -> !k.trim().isEmpty())
                .orElseThrow(() -> new IllegalArgumentException("Публичный ключ обязателен при регистрации!"));


        String encodedPassword = passwordEncoder.encode(rawPassword);

        User newUser = User.builder()
                .username(username)
                .password(encodedPassword)
                .email(email)
                .fcmToken(fcmToken)
                .publicKey(clientPublicKey)
                .build();

        userRepository.save(newUser);

        String token = jwtUtil.generateToken(newUser.getUsername(), newUser.getId());

        try {
            emailService.sendWelcomeEmail(email, username);
        } catch (Exception e) {
            System.err.println("⚠️ Welcome email failed: " + e.getMessage());
        }

        Map<String, String> response = new HashMap<>();
        response.put("message", "Поздравляю! Вы в ElisMessenger!");
        response.put("token", token);
        response.put("userId", String.valueOf(newUser.getId()));
        return response;
    }

    // Авторизуем пользователя
    public Map<String, String> login(String username, String rawPassword, String fcmToken, String clientPublicKey) {

        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new IllegalArgumentException("Пользователь с таким логином не найден!"));

        Optional.of(user)
                .filter(u -> passwordEncoder.matches(rawPassword, u.getPassword()))
                .orElseThrow(() -> new IllegalArgumentException("Неверный пароль!"));

        user.setFcmToken(fcmToken);

        // 🚀 ГЛАВНОЕ ИЗМЕНЕНИЕ: НЕ перезаписываем ключ, если он уже есть
        if (user.getPublicKey() == null || user.getPublicKey().isEmpty()) {
            if (clientPublicKey != null && !clientPublicKey.isEmpty()) {
                user.setPublicKey(clientPublicKey);
                System.out.println("🔑 [E2EE] Первый ключ сохранён для " + username);
            }
        } else {
            System.out.println("🔑 [E2EE] Ключ для " + username + " уже существует, не перезаписываем.");
        }

        userRepository.save(user);

        String token = jwtUtil.generateToken(user.getUsername(), user.getId());

        Map<String, String> response = new HashMap<>();
        response.put("token", token);
        response.put("userId", String.valueOf(user.getId()));
        response.put("message", "С возвращением в ElisMessenger!");

        if (user.getPublicKey() != null && !user.getPublicKey().isEmpty()) {
            response.put("publicKey", user.getPublicKey());
        }

        return response;
    }

    @Transactional
    public Map<String, String> requestPasswordReset(String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new IllegalArgumentException("Пользователь с такой почтой не зарегистрирован!"));

        // Удаляем старые коды этого юзера
        resetCodeRepository.deleteAllByUser(user);

        String plainCode = String.format("%06d", new Random().nextInt(1_000_000));

        PasswordResetCode code = PasswordResetCode.builder()
                .user(user)
                .codeHash(passwordEncoder.encode(plainCode)) // хэшируем!
                .expiresAt(LocalDateTime.now().plusMinutes(10)) // TTL 10 минут
                .attempts(0)
                .build();
        resetCodeRepository.save(code);

        emailService.sendResetCodeEmail(email, user.getUsername(), plainCode);

        // ⚠️ debugCode убран (на реальном сервере это уязвимость)
        return Map.of("message", "Код восстановления отправлен на вашу почту!");
    }

    @Transactional
    public Map<String, String> resetPassword(String email, String code, String newRawPassword) {
        Optional.ofNullable(newRawPassword)
                .filter(p -> !p.trim().isEmpty())
                .orElseThrow(() -> new IllegalArgumentException("Новый пароль не может быть пустым!"));

        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new IllegalArgumentException("Пользователь не найден!"));

        PasswordResetCode resetCode = resetCodeRepository
                .findTopByUserOrderByCreatedAtDesc(user)
                .orElseThrow(() -> new IllegalArgumentException("Код не запрашивался или просрочен!"));

        // Проверка TTL
        if (resetCode.getExpiresAt().isBefore(LocalDateTime.now())) {
            resetCodeRepository.delete(resetCode);
            throw new IllegalArgumentException("Код просрочен. Запросите новый.");
        }

        // Лимит попыток (брутфорс)
        if (resetCode.getAttempts() >= 5) {
            resetCodeRepository.delete(resetCode);
            throw new IllegalArgumentException("Слишком много попыток. Запросите новый код.");
        }

        // Проверка кода
        if (!passwordEncoder.matches(code, resetCode.getCodeHash())) {
            resetCode.setAttempts(resetCode.getAttempts() + 1);
            resetCodeRepository.save(resetCode);
            throw new IllegalArgumentException("Неверный код восстановления!");
        }

        // Успех
        user.setPassword(passwordEncoder.encode(newRawPassword));
        userRepository.save(user);
        resetCodeRepository.deleteAllByUser(user);

        return Map.of("message", "Пароль успешно изменен! Войдите с новым паролем.");
    }

    @Transactional
    public void updatePublicKey(String username, String publicKey) {
        if (publicKey == null || publicKey.isBlank()) {
            throw new IllegalArgumentException("Публичный ключ не передан");
        }

        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new IllegalArgumentException("Пользователь не найден"));

        // 🚀 ПРИНУДИТЕЛЬНО ПЕРЕЗАПИСЫВАЕМ КЛЮЧ
        user.setPublicKey(publicKey);
        userRepository.save(user);
        System.out.println("🔑 [E2EE] Ключ для " + username + " ПРИНУДИТЕЛЬНО ОБНОВЛЕН.");
    }

    @Scheduled(fixedDelay = 3_600_000)
    @Transactional
    public void cleanupExpiredResetCodes() {
        int deleted = resetCodeRepository.deleteExpired(LocalDateTime.now());
        if (deleted > 0) {
            System.out.println("🧹 Удалено просроченных reset-кодов: " + deleted);
        }
    }

    @PostMapping("/debug/reset-key/{username}")
    public ResponseEntity<?> resetKey(@PathVariable String username) {
        User user = userRepository.findByUsername(username).orElseThrow();
        user.setPublicKey(null);
        userRepository.save(user);
        return ResponseEntity.ok("Ключ для " + username + " сброшен. Зайдите в приложение заново.");
    }
}