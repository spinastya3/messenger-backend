package com.example.messenger;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.messenger.controller.UserController;
import com.example.messenger.model.User;
import com.example.messenger.repository.MessageRepository;
import com.example.messenger.repository.UserRepository;
import com.example.messenger.util.JwtUtil;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

@WebMvcTest(value = UserController.class, excludeAutoConfiguration = SecurityAutoConfiguration.class)
public class UserControllerTests {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private UserRepository userRepository;

    @MockBean
    private JwtUtil jwtUtil;

    @MockBean
    private MessageRepository messageRepository;

    @Test
    public void shouldReturnUsers_WhenUsernameMatches() throws Exception {
        String searchName = "Мама";
        User mockUser = User.builder().id(1L).username("Мама").email("mama@mail.com").build();

        // Обучаем репозиторий отдавать наш фейковый список при поиске
        when(userRepository.findByUsernameContainingIgnoreCase(searchName)).thenReturn(List.of(mockUser));

        // 🚀 ВЫСТРЕЛИВАЕМ ТЕСТОВЫМ ЗАПРОСОМ С ПАРАМЕТРОМ:
        mockMvc.perform(get("/api/users/search")
                        .param("username", searchName)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].username").value("Мама"))
                .andExpect(jsonPath("$[0].id").value(1));

        verify(userRepository, times(1)).findByUsernameContainingIgnoreCase(searchName);
    }

    @Test
    public void shouldReturnEmptyList_WhenUsernameIsEmpty() throws Exception {
        // Если строка пустая, контроллер даже не должен дергать базу данных Postgres!
        mockMvc.perform(get("/api/users/search")
                        .param("username", "   ")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isEmpty()); // Ждем пустой массив []

        // Железная проверка: метод репозитория ХОЛОСТЫМ ходом не вызывался!
        verifyNoInteractions(userRepository);
    }
}

