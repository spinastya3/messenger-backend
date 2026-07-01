package com.example.messenger;

import com.example.messenger.controller.UserController;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;

@SpringBootTest // Запускаем весь сервер
@ActiveProfiles("test") // Включаем базу H2 из application-test.properties
public class UserControllerIntegrationTests {

    private MockMvc mockMvc;

    @Autowired
    private UserController userController;

    @BeforeEach
    public void setUp() {
        this.mockMvc = MockMvcBuilders.standaloneSetup(userController).build();
    }

    @Test
    public void testLoginConvertsToLowerCaseAndRegistersInH2() throws Exception {
        mockMvc.perform(post("/api/users/login")
                        .param("username", "   ГАРРИ   "))
                .andExpect(status().isCreated()) // Наш статус 201 Created!
                .andExpect(jsonPath("$.username").value("гарри")) // Проверка очистки капса
                .andExpect(jsonPath("$.id").exists()); // Проверка генерации ID в H2
    }
}
