package com.example.messenger.service;

import com.example.messenger.crypto.CryptoHandshakeUtil;
import com.example.messenger.dto.RefreshSessionResponse;
import com.example.messenger.model.User;
import com.example.messenger.repository.UserRepository;
import com.example.messenger.util.JwtUtil;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;
    private final EmailService emailService;
    private final CryptoHandshakeUtil cryptoHandshakeUtil;


    private final Map<String, String> resetCodesCache = new ConcurrentHashMap<>();

    public AuthService(UserRepository userRepository, PasswordEncoder passwordEncoder,
                       JwtUtil jwtUtil, EmailService emailService,
                       CryptoHandshakeUtil cryptoHandshakeUtil) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtUtil = jwtUtil;
        this.emailService = emailService;
        this.cryptoHandshakeUtil = cryptoHandshakeUtil;
    }

    // Регистрируем пользователя, если логин уникальный
    public Map<String, String> register(String username, String rawPassword, String email, String fcmToken,  String clientPublicKey) {

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

        // Шифруем пароль
        String encodedPassword = passwordEncoder.encode(rawPassword);

        // Собюираем юзера
        User newUser = User.builder()
                .username(username)
                .password(encodedPassword)
                .email(email)
                .fcmToken(fcmToken)
                .build();

        // Сохраняем юзера в БД
        userRepository.save(newUser);

        emailService.sendWelcomeEmail(email, username);

        Map<String, String> response = new HashMap<>();
        response.put("message", "Поздравляю! Вы в ElisMessenger!");

        // Вызываем утилиту рукопожатия
        //  cryptoHandshakeUtil.processHandshake(username, clientPublicKey, response);

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
        userRepository.save(user);

        String token = jwtUtil.generateToken(user.getUsername(), user.getId());

        Map<String, String> response = new HashMap<>();
        response.put("token", token);
        response.put("userId", String.valueOf(user.getId()));
        response.put("message", "С возвращением в ElisMessenger!");

        cryptoHandshakeUtil.processHandshake(username, clientPublicKey, response);

        return response;
    }

    // Запрашиваем изменение пароля
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

    // Меняем пароль
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

    public RefreshSessionResponse refreshSession(String username, String clientPublicKey) {
        if (clientPublicKey == null || clientPublicKey.trim().isEmpty()) {
            throw new IllegalArgumentException("Client public key is missing");
        }

        // Находим пользователя в базе данных, чтобы получить его ID
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new IllegalArgumentException("Пользователь не найден"));

        // Временно создаем мапу для утилиты рукопожатия, куда она сама положит "serverPublicKey"
        Map<String, String> handshakeMap = new HashMap<>();
        cryptoHandshakeUtil.processHandshake(username, clientPublicKey, handshakeMap);

        String serverPublicKey = handshakeMap.get("serverPublicKey");

        // Возвращаем строго типизированный красивый DTO-ответ
        return new com.example.messenger.dto.RefreshSessionResponse(serverPublicKey, String.valueOf(user.getId()));
    }
}
