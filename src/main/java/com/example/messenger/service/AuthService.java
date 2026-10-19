package com.example.messenger.service;

import com.example.messenger.model.User;
import com.example.messenger.repository.UserRepository;
import com.example.messenger.util.JwtUtil;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;
    private final EmailService emailService;
    private final Map<String, String> resetCodesCache = new ConcurrentHashMap<>();

    public AuthService(UserRepository userRepository, PasswordEncoder passwordEncoder,
                       JwtUtil jwtUtil, EmailService emailService) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtUtil = jwtUtil;
        this.emailService = emailService;
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

        String encodedPassword = passwordEncoder.encode(rawPassword);

        User newUser = User.builder()
                .username(username)
                .password(encodedPassword)
                .email(email)
                .fcmToken(fcmToken)
                .publicKey(clientPublicKey)
                .build();

        userRepository.save(newUser);
        emailService.sendWelcomeEmail(email, username);

        Map<String, String> response = new HashMap<>();
        response.put("message", "Поздравляю! Вы в ElisMessenger!");
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

        return response;
    }

    // ... остальные методы (requestPasswordReset, resetPassword, updatePublicKey) без изменений ...

    public Map<String, String> requestPasswordReset(String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new IllegalArgumentException("Пользователь с такой почтой не зарегистрирован!"));

        String resetCode = String.format("%06d", new Random().nextInt(1000000));
        resetCodesCache.put(email, resetCode);

        emailService.sendResetCodeEmail(email, user.getUsername(), resetCode);

        return Map.of(
                "message", "Код восстановления отправлен на вашу почту!",
                "debugCode", resetCode
        );
    }

    public Map<String, String> resetPassword(String email, String code, String newRawPassword) {
        Optional.ofNullable(newRawPassword)
                .filter(p -> !p.trim().isEmpty())
                .orElseThrow(() -> new IllegalArgumentException("Новый пароль не может быть пустым!"));

        Optional.ofNullable(resetCodesCache.get(email))
                .filter(c -> c.equals(code))
                .orElseThrow(() -> new IllegalArgumentException("Неверный или просроченный код восстановления!"));

        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new IllegalArgumentException("Пользователь не найден!"));

        user.setPassword(passwordEncoder.encode(newRawPassword));
        userRepository.save(user);
        resetCodesCache.remove(email);

        return Map.of("message", "Пароль успешно изменен! Войдите с новым паролем.");
    }

    public void updatePublicKey(String username, String publicKey) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new IllegalArgumentException("Пользователь не найден"));

        // 🚀 ГЛАВНОЕ ИЗМЕНЕНИЕ: не перезаписываем, если ключ уже есть
        if (user.getPublicKey() != null && !user.getPublicKey().isEmpty()) {
            System.out.println("🔑 [E2EE] Ключ для " + username + " уже существует. Пропускаем обновление.");
            return;
        }

        user.setPublicKey(publicKey);
        userRepository.save(user);
        System.out.println("🔑 [E2EE] Первый ключ сохранён для " + username);
    }
}