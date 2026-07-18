package com.example.messenger;

import com.example.messenger.config.WebSocketConfig;
import com.example.messenger.repository.MessageRepository;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test") // Профиль "test" теперь автоматически выключит WebSocketConfig!
class MessengerApplicationTests {

    @MockBean
    private MessageRepository messageRepository;

    @MockBean
    private SimpMessagingTemplate simpMessagingTemplate;

    @Test
    void contextLoads() {
        // Контекст Спринга теперь взлетит со скоростью звука!
    }
}
