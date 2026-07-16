package com.example.messenger;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
class MessengerApplicationTests {

    @MockBean
    private com.example.messenger.repository.MessageRepository messageRepository;

    @MockBean
    private org.springframework.messaging.simp.SimpMessagingTemplate simpMessagingTemplate;
    @Test
    void contextLoads() {
    }
}
