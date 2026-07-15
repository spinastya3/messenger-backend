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
