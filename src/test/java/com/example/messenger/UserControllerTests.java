package com.example.messenger;

import com.example.messenger.controller.UserController;
import com.example.messenger.model.User;
import com.example.messenger.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class UserControllerTests {

    @InjectMocks
    private UserController userController;

    @Mock
    private UserRepository userRepository;

    private User expectedDatabaseUser;

    @BeforeEach
    public void setUp(){

        expectedDatabaseUser = new User();
        expectedDatabaseUser.setId(999L);
        expectedDatabaseUser.setUsername("поттер");
        expectedDatabaseUser.setFcmToken("secret_fcm_token_666");
    }

    @Test
    public void returnIdFromBaseTest(){

        User inputUser = new User();
        inputUser.setUsername("   ПОТТЕР   ");

        when(userRepository.findByUsername("поттер")).thenReturn(Optional.empty());
        when(userRepository.save(any(User.class))).thenReturn(expectedDatabaseUser);

        User result = userController.loginOrRegister(inputUser).getBody();

        assertAll("Комплексная проверка ручки авторизации",
                () -> assertNotNull(result, "ID не должен быть null"),
                () -> assertEquals(999L, result.getId(), "Контроллер должен вернуть ID из базы"),
                () -> assertEquals("поттер", result.getUsername(), "Имя должно быть без капса и без пробелов"),
                () -> assertEquals("secret_fcm_token_666", result.getFcmToken(), "Контроллер обязан вернуть сохраненный FCM-токен")
        );
    }

    @Test
    public void getAllUsersTest(){

        User secondUser = new User();
        secondUser.setId(15L);
        secondUser.setUsername("гарри");

        java.util.List<User> mockUsersList = java.util.Arrays.asList(expectedDatabaseUser, secondUser);

        when(userRepository.findAll()).thenReturn(mockUsersList);

        List<User> result = userController.getAllUsers().getBody();

        assertAll("Проверка списка пользователей",
                () -> assertNotNull(result, "Список не должен быть null"),
                () -> assertEquals(2, result.size(), "Размер списка должен быть 2"),
                () -> assertEquals("поттер", result.getFirst().getUsername(), "Первым должен быть Поттер"),
                () -> assertEquals("гарри", result.get(1).getUsername(), "Вторым должен быть Гарри")
        );
    }
}
