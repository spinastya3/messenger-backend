package com.example.messenger.service;

import com.example.messenger.model.User;
import com.example.messenger.repository.UserRepository;
import com.example.messenger.util.JwtUtil;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;
    private final EmailService emailService; // 📧 Подключаем почтовик!

    private final Map<String, String> resetCodesCache = new ConcurrentHashMap<>();

    public AuthService(UserRepository userRepository, PasswordEncoder passwordEncoder, JwtUtil jwtUtil, EmailService emailService) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtUtil = jwtUtil;
        this.emailService = emailService;
    }

    // Регистрируем пользователя, если логин уникальный
    public Map<String, String> register(String username, String rawPassword, String email, String fcmToken) {

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

        return Map.of("message", "Поздравляю! Вы в ElisMessenger!");
    }

    // Авторизуем пользователя
    public Map<String, String> login(String username, String rawPassword, String fcmToken) {

        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new IllegalArgumentException("Пользователь с таким логином не найден!"));

        Optional.of(user)
                .filter(u -> passwordEncoder.matches(rawPassword, u.getPassword()))
                .orElseThrow(() -> new IllegalArgumentException("Неверный пароль!"));

        user.setFcmToken(fcmToken);
        userRepository.save(user);

        String token = jwtUtil.generateToken(user.getUsername(), user.getId());

        return Map.of(
                "token", token,
                "userId", String.valueOf(user.getId()),
                "message", "С возвращением в ElisMessenger!"
        );
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
}
