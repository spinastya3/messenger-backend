package com.example.messenger;

import com.example.messenger.controller.UserController;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;


import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;

@SpringBootTest // Запускаем весь сервер
@ActiveProfiles("test")
@AutoConfigureMockMvc
public class UserControllerIntegrationTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserController userController;

    @Test
    public void testLoginConvertsToLowerCaseAndRegistersInH2() throws Exception {

        String jsonRequestBody = "{\"username\":\"   ГАРРИ   \"}";

        mockMvc.perform(post("/api/users/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonRequestBody))
                .andExpect(status().isCreated()) // Наш статус 201 Created!
                .andExpect(jsonPath("$.username").value("гарри")) // Проверка очистки капса
                .andExpect(jsonPath("$.id").exists()); // Проверка генерации ID в H2
    }
}
