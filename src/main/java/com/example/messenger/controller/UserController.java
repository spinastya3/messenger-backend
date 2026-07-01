package com.example.messenger.controller;

import com.example.messenger.model.User;
import com.example.messenger.repository.UserRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import lombok.RequiredArgsConstructor;
import java.util.List;
import java.util.Optional;

@RestController
@RequestMapping("/api/users") // Все запросы будут начинаться с этого адреса
@RequiredArgsConstructor
public class UserController {

    private final UserRepository userRepository;

    // Ручка входа и регистрации (POST-запрос)
    @PostMapping("/login")
    public ResponseEntity<User> loginOrRegister(@RequestParam String username) {

        // Имя пользователя приводим к одному виду, не зависим от капса
        String cleanUsername = username.trim().toLowerCase();

        // Проверяем есть ли пользователь с таким именем
        Optional<User> existingUser = userRepository.findByUsername(cleanUsername);

        if (existingUser.isPresent()) {
            // Если пользователь найден возвращаем его со статусом 200 OK
            return org.springframework.http.ResponseEntity.ok(existingUser.get());
        } else {
            // Создаем, сохраняем и возвращаем пользователя со статусом 201 Created
            User newUser = new User();
            newUser.setUsername(cleanUsername);
            User savedUser = userRepository.save(newUser);

            return org.springframework.http.ResponseEntity
                    .status(org.springframework.http.HttpStatus.CREATED)
                    .body(savedUser);
        }
    }


    // Ручка для списка контактов (GET-запрос)
    @GetMapping("/all")
    public ResponseEntity<List<User>> getAllUsers() {
        List<User> users = userRepository.findAll();
        return ResponseEntity.ok(users);
    }

    // Ручка для удаления пользователя из БД (DELETE-запрос)
    @DeleteMapping("/delete/{id}")
    public ResponseEntity<String> deleteUser(@PathVariable Long id){
        if (userRepository.existsById(id)){
            userRepository.deleteById(id);
            return  ResponseEntity.ok("Пользователь с ID " + id + " удален");
        } else {
            return ResponseEntity
                    .status(org.springframework.http.HttpStatus.NOT_FOUND)
                    .body("Пользователь не найден");
        }
    }
}
