package com.example.messenger.controller;

import com.example.messenger.model.User;
import com.example.messenger.repository.MessageRepository;
import com.example.messenger.repository.UserRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.*;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import lombok.RequiredArgsConstructor;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/users") // Все запросы будут начинаться с этого адреса
@RequiredArgsConstructor
@Tag(name = "User Controller", description = "Управление профилями пользователей и безопасным поиском контактов")
public class UserController {

    private final UserRepository userRepository;
    private final MessageRepository messageRepository;

    @Operation(
            summary = "Поиск пользователей",
            description = "Ищет зарегистрированных людей в базе Postgres по буквам логина, полностью игнорируя регистр (большие/маленькие буквы)."
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Поиск успешно отработал. Возвращает список совпавших пользователей (или пустой массив).")
    })
    @GetMapping("/search")
    public ResponseEntity<List<User>> searchUsers(@RequestParam("username") String username) {
        if (username == null || username.trim().length() < 3) {
            return ResponseEntity.ok(List.of());
        }
        List<User> foundUsers = userRepository.searchUsersByUsernameIgnoreCase(username.trim());
        return ResponseEntity.ok(foundUsers);
    }

    @Operation(
            summary = "Получить список активных диалогов пользователя",
            description = "Вытаскивает из базы Postgres только тех пользователей, с кем у текущего юзера есть история сообщений."
    )
    @GetMapping("/active-chats/{userId}")
    public ResponseEntity<List<User>> getActiveChats(@PathVariable Long userId) {
        List<Long> buddyIds = messageRepository.findActiveChatBuddyIds(userId);
        if (buddyIds.isEmpty()) {
            return ResponseEntity.ok(List.of());
        }
        List<User> activeUsers = userRepository.findAllById(buddyIds);
        return ResponseEntity.ok(activeUsers);
    }

    @GetMapping("/{id}/public-key")
    public ResponseEntity<?> getUserPublicKey(@PathVariable Long id) {
        return userRepository.findById(id)
                .map(user -> {
                    if (user.getPublicKey() == null || user.getPublicKey().isEmpty()) {
                        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                                .body(Map.of("error", "У пользователя пока нет публичного ключа"));
                    }
                    return ResponseEntity.ok(Map.of("publicKey", user.getPublicKey()));
                })
                .orElse(ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(Map.of("error", "Пользователь не найден")));
    }
}
