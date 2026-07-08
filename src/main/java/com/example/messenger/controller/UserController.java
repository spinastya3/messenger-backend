package com.example.messenger.controller;

import com.example.messenger.model.User;
import com.example.messenger.repository.UserRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.*;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import lombok.RequiredArgsConstructor;
import java.util.List;
import java.util.Optional;

import static org.springframework.http.HttpStatus.*;


@RestController
@RequestMapping("/api/users") // Все запросы будут начинаться с этого адреса
@RequiredArgsConstructor
public class UserController {

    private final UserRepository userRepository;

    @Operation(
            summary = "Вход или регистрация пользователя",
            description = "Если пользователь с таким именем уже есть, возвращает статус 200. Если пользователя нет — создает нового со статусом 201."
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Пользователь успешно найден и авторизован"),
            @ApiResponse(responseCode = "201", description = "Новый пользователь успешно создан в базе данных"),
            @ApiResponse(responseCode = "400", description = "Некорректный запрос: имя пользователя пустое или состоит только из пробелов")

    })

    // Ручка входа и регистрации (POST-запрос)
    @PostMapping("/login")
    public ResponseEntity<User> loginOrRegister(@RequestBody User user) {

        String username= user.getUsername();
        // Имя пользователя приводим к одному виду, не зависим от капса

        if (username== null || username.isBlank()) {
            return ResponseEntity.badRequest().build();
        }

        String cleanUsername = username.trim().toLowerCase();

        // Проверяем есть ли пользователь с таким именем
        Optional<User> existingUser = userRepository.findByUsername(cleanUsername);

        if (existingUser.isPresent()) {

            User foundUser = existingUser.get();
            foundUser.setFcmToken(user.getFcmToken());
            User savedUser = userRepository.save(foundUser);
            // Если пользователь найден возвращаем его со статусом 200 OK
            return ResponseEntity.ok(savedUser);
        } else {
            // Создаем, сохраняем и возвращаем пользователя со статусом 201 Created
            User newUser = new User();
            newUser.setUsername(cleanUsername);
            newUser.setFcmToken(user.getFcmToken());
            User savedUser = userRepository.save(newUser);

            return ResponseEntity
                    .status(CREATED)
                    .body(savedUser);
        }
    }


    // Ручка для списка контактов (GET-запрос)
    @Operation(
            summary = "Получить список всех зарегистрированных пользователей",
            description = "Вытаскивает из базы данных Postgres абсолютно всех юзеров для экрана контактов мобилки."
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Список пользователей успешно получен")
    })

    @GetMapping("/all")
    public ResponseEntity<List<User>> getAllUsers() {
        List<User> users = userRepository.findAll();
        return ResponseEntity.ok(users);
    }

    // Ручка для удаления пользователя из БД (DELETE-запрос)
    @Operation(
            summary = "Удалить пользователя по его ID",
            description = "Жестко вырезает пользователя из таблицы Postgres. Полезно при тестах, чтобы стирать мусорные профили."
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
