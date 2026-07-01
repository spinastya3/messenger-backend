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

    private User savedUserMock;
    private final String firstUser = "поттер";

    @BeforeEach
    public void setUp() {
        savedUserMock = new User();
        savedUserMock.setId(999L);
        savedUserMock.setUsername(firstUser);
    }


    @Test
    public void nameToLowerCaseAndTrimTest(){

        String inputName = "   ПОТТЕР   ";

        when(userRepository.findByUsername(firstUser)).thenReturn(Optional.empty());
        when(userRepository.save(any(User.class))).thenReturn(savedUserMock);

        User result = userController.loginOrRegister(inputName).getBody();
        assertEquals(firstUser, result.getUsername());
    }

    @Test
    public void returnIdFromBaseTest(){

        when(userRepository.findByUsername(firstUser)).thenReturn(Optional.empty());
        when(userRepository.save(any(User.class))).thenReturn(savedUserMock);

        User result = userController.loginOrRegister(firstUser).getBody();
        long firstUserID = 999L;
        assertAll("Проверка генерации ID",
                () -> assertNotNull(result.getId(), "ID не должен быть null"),
                () -> assertEquals(firstUserID, result.getId(), "Контроллер должен вернуть ID из базы")
        );
    }

    @Test
    public void getAllUsersTest(){

        User secondUser = new User();
        secondUser.setId(15L);
        secondUser.setUsername("гарри");

        java.util.List<User> mockUsersList = java.util.Arrays.asList(savedUserMock, secondUser);

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
