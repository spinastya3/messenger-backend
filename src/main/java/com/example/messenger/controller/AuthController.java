package com.example.messenger.controller;

import com.example.messenger.service.AuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import java.util.Map;

import static org.springframework.http.HttpStatus.*;

@RestController
@RequestMapping("/api/auth")
@Tag(name = "Authentication Controller", description = "Управление регистрацией, входом и безопасностью пользователей")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @Operation(summary = "Регистрация нового пользователя", description = "Принимает уникальный логин, пароль и валидный email. Пароль автоматически хэшируется через BCrypt.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "201", description = "Пользователь успешно создан в базе Postgres."),
            @ApiResponse(responseCode = "400", description = "Пустые поля или неверный формат email)"),
            @ApiResponse(responseCode = "409", description = "Логин или почта уже заняты другим пользователем)")
    })
    @PostMapping("/register")
    public ResponseEntity<?> registerUser(@RequestBody Map<String, String> request) {
        try {
            String username = request.get("username");
            String password = request.get("password");
            String email = request.get("email");

            Map<String, String> response = authService.register(username, password, email);

            return ResponseEntity.status(CREATED).body(response);

        } catch (IllegalArgumentException e) {
            String msg = e.getMessage();
            if (msg.contains("уже существует")) {
                return ResponseEntity.status(CONFLICT).body(Map.of("error", msg));
            }
            return ResponseEntity.status(BAD_REQUEST).body(Map.of("error", msg));
        }
    }

    @Operation(summary = "Авторизация пользователя (Вход)", description = "Проверяет BCrypt-пароль по базе данных. При успешном совпадении генерирует и возвращает секретный JWT-токен.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Успешный вход. Возвращает JWT-токен сессии."),
            @ApiResponse(responseCode = "401", description = "Пользователь не найден или пароль не подошёл)")
    })
    @PostMapping("/login")
    public ResponseEntity<?> loginUser(@RequestBody Map<String, String> request) {
        try {
            String username = request.get("username");
            String password = request.get("password");

            Map<String, String> response = authService.login(username, password);
            return ResponseEntity.ok(response);

        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(UNAUTHORIZED).body(Map.of("error", e.getMessage()));
        }
    }

    @Operation(summary = "Запросить код восстановления пароля", description = "Проверяет наличие Email в Postgres, генерирует 6-значный OTP код и шлет его на почту.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Код успешно сгенерирован и отправлен."),
            @ApiResponse(responseCode = "400", description = "Ошибка: пользователь с таким Email не существует в системе")
    })
    @PostMapping("/forgot-password")
    public ResponseEntity<?> forgotPassword(@RequestBody Map<String, String> request) {
        try {
            String email = request.get("email");
            Map<String, String> response = authService.requestPasswordReset(email);
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @Operation(summary = "Подтвердить код и установить новый пароль", description = "Сверяет 6-значный код с кэшем сервера. В случае успеха хэширует новый пароль через BCrypt.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Пароль успешно обновлен в базе данных."),
            @ApiResponse(responseCode = "400", description = "Ошибка: неверный код восстановления, пустой пароль или пользователь не найден")
    })
    @PostMapping("/reset-password")
    public ResponseEntity<?> resetPassword(@RequestBody Map<String, String> request) {
        try {
            String email = request.get("email");
            String code = request.get("code");
            String newPassword = request.get("password");

            Map<String, String> response = authService.resetPassword(email, code, newPassword);
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }
}
