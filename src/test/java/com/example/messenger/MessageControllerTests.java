package com.example.messenger;

import com.example.messenger.controller.MessageController;
import com.example.messenger.dto.MessageDto;
import com.example.messenger.model.Message;
import com.example.messenger.model.MessageStatus;
import com.example.messenger.model.User;
import com.example.messenger.repository.MessageRepository;
import com.example.messenger.repository.UserRepository;
import com.example.messenger.service.MessageService;
import com.example.messenger.service.PushNotificationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import java.security.Principal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class MessageControllerTests {

    private MessageController messageController;

    @Mock
    private SimpMessagingTemplate messagingTemplate;
    @Mock
    private MessageRepository messageRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private PushNotificationService pushNotificationService;
    @Mock
    private MessageService messageService;

    @BeforeEach
    public void setUp() {
        messageController = new MessageController(
                messagingTemplate,
                messageRepository,
                userRepository,
                pushNotificationService,
                messageService
        );
    }

    @Test
    public void chatHistoryTest() {
        long testSenderId = 1L;
        long testRecipientId = 2L;
        LocalDateTime expectedTime = LocalDateTime.of(2026, 6, 30, 14, 0, 0);

        Principal mockPrincipal = mock(Principal.class);

        User sender = User.builder().id(testSenderId).username("Гарри").build();
        User recipient = User.builder().id(testRecipientId).username("Рон").build();

        Message testMessage = new Message();
        testMessage.setId(555L);
        testMessage.setSender(sender);
        testMessage.setRecipient(recipient);
        testMessage.setContent("ЗАШИФРОВАННЫЙ_ТЕКСТ_ОТ_МОБИЛКИ");
        testMessage.setTimestamp(expectedTime);
        testMessage.setStatus(MessageStatus.SENT);

        List<Message> testHistory = List.of(testMessage);

        // Мокаем ТОЛЬКО метод, который вызывается
        when(messageService.getChatHistory(eq(testSenderId), eq(testRecipientId), anyInt(), anyInt()))
                .thenReturn(testHistory);

        // Вызов контроллера
        ResponseEntity<?> response = messageController.getChatHistory(testSenderId, testRecipientId, 0, 20, mockPrincipal);

        assertNotNull(response.getBody(), "Тело ответа не должно быть null");
        List<MessageDto> result = (List<MessageDto>) response.getBody();

        assertAll("Проверка выгрузки истории чата через DTO (E2EE)",
                () -> assertEquals(1, result.size(), "В истории не одно сообщение"),
                () -> assertEquals("ЗАШИФРОВАННЫЙ_ТЕКСТ_ОТ_МОБИЛКИ", result.get(0).getContent(), "Сервер не должен менять шифротекст"),
                () -> assertEquals(expectedTime, result.get(0).getTimestamp(), "Время сообщения не совпадает"),
                () -> assertEquals(testSenderId, result.get(0).getSenderId(), "ID отправителя в DTO не совпадает")
        );
    }
    @Test
    public void processMessage_ShouldSetCurrentTimestampAndSave() {
        User senderUser = new User();
        senderUser.setId(10L);
        senderUser.setUsername("гарри");

        User recipientUser = new User();
        recipientUser.setId(20L);
        recipientUser.setUsername("гермиона");

        Message localTestMessage = new Message();
        localTestMessage.setSender(senderUser);
        localTestMessage.setRecipient(recipientUser);
        localTestMessage.setContent("ЗАШИФРОВАННЫЙ_ТЕКСТ_ОТ_МОБИЛКИ");

        when(userRepository.findById(eq(10L))).thenReturn(Optional.of(senderUser));
        when(userRepository.findById(eq(20L))).thenReturn(Optional.of(recipientUser));
        when(messageRepository.save(any(Message.class))).then(org.mockito.AdditionalAnswers.returnsFirstArg());

        LocalDateTime testStartTime = LocalDateTime.now().minusSeconds(1);

        // Делаем выстрел в контроллер!
        messageController.processMessage(localTestMessage);

        LocalDateTime messageTime = localTestMessage.getTimestamp();

        assertAll("Проверка генерации живого тайминга при отправке",
                () -> assertNotNull(messageTime, "Сервер обязан сгенерировать timestamp!"),
                () -> assertTrue(messageTime.isAfter(testStartTime), "Тайминг должен быть актуальным"),
                // 🚀 ГЛАВНОЕ: Сервер НЕ расшифровывает!
                () -> assertEquals("ЗАШИФРОВАННЫЙ_ТЕКСТ_ОТ_МОБИЛКИ", localTestMessage.getContent(), "Сервер не должен менять шифротекст")
        );

        // Проверяем, что сообщение разослано в сокеты (получателю и отправителю)
        verify(messagingTemplate, times(2)).convertAndSend(anyString(), any(Message.class));
    }

    @Test
    public void shouldSendPushNotificationWhenMessageArrives() {
        User senderUser = new User();
        senderUser.setId(10L);
        senderUser.setUsername("гарри");

        User recipientUser = new User();
        recipientUser.setId(20L);
        recipientUser.setUsername("гермиона");

        User databaseRecipient = new User();
        databaseRecipient.setId(20L);
        databaseRecipient.setUsername("гермиона");
        databaseRecipient.setFcmToken("real_fcm_token_666");

        Message incomingMessage = new Message();
        incomingMessage.setSender(senderUser);
        incomingMessage.setRecipient(recipientUser);
        incomingMessage.setContent("ЗАШИФРОВАННЫЙ_ТЕКСТ_ОТ_МОБИЛКИ");

        when(messageRepository.save(any(Message.class))).then(org.mockito.AdditionalAnswers.returnsFirstArg());
        when(userRepository.findById(eq(20L))).thenReturn(Optional.of(databaseRecipient));
        when(userRepository.findById(eq(10L))).thenReturn(Optional.of(senderUser));

        // Делаем выстрел в контроллер!
        messageController.processMessage(incomingMessage);

        assertNotNull(incomingMessage.getTimestamp(), "Сообщение успешно обработано сервером");

        // 🚀 ГЛАВНОЕ: Пуш летит с заглушкой
        verify(pushNotificationService)
                .sendPushNotification(
                        eq("real_fcm_token_666"),
                        eq("гарри"),
                        eq("Новое сообщение"), // Заглушка
                        eq(10L),
                        eq("гарри")
                );
    }
}
