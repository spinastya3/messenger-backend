package com.example.messenger.controller;

import com.example.messenger.model.User;
import com.example.messenger.repository.MessageRepository;
import com.example.messenger.repository.UserRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.*;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import lombok.RequiredArgsConstructor;
import java.util.List;

import static org.springframework.http.HttpStatus.*;


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

    @Operation(
            summary = "Удалить пользователя по его ID",
            description = "Удаляет пользователя из таблицы Postgres. Для тестов"
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Пользователь успешно стерт из базы данных"),
            @ApiResponse(responseCode = "404", description = "Ошибка: пользователя с таким ID не существует в Postgres")
    })
    @DeleteMapping("/delete/{id}")
    public ResponseEntity<String> deleteUser(@PathVariable Long id){
        if (userRepository.existsById(id)){
            userRepository.deleteById(id);
            return  ResponseEntity.ok("Пользователь с ID " + id + " удален");
        } else {
            return ResponseEntity
                    .status(NOT_FOUND)
                    .body("Пользователь не найден");
        }
    }
}
