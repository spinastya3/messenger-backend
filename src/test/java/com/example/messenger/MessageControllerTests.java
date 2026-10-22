package com.example.messenger;

import com.example.messenger.controller.MessageController;
import com.example.messenger.dto.MessageDto;
import com.example.messenger.dto.SendMessageRequest;
import com.example.messenger.model.*;
import com.example.messenger.repository.MessagePayloadRepository;
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
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class MessageControllerTests {

    private MessageController messageController;

    @Mock private SimpMessagingTemplate messagingTemplate;
    @Mock private MessageRepository messageRepository;
    @Mock private MessagePayloadRepository payloadRepository;
    @Mock private UserRepository userRepository;
    @Mock private PushNotificationService pushNotificationService;
    @Mock private MessageService messageService;

    @BeforeEach
    public void setUp() {
        messageController = new MessageController(
                messagingTemplate,
                messageRepository,
                payloadRepository,
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
        when(mockPrincipal.getName()).thenReturn("Гарри");

        User sender = User.builder().id(testSenderId).username("Гарри").build();

        // 🚀 Теперь сервис возвращает List<MessageDto>, а не List<Message>
        MessageDto testDto = MessageDto.builder()
                .id(555L)
                .senderId(testSenderId)
                .senderName("Гарри")
                .recipientId(testRecipientId)
                .content("ЗАШИФРОВАННЫЙ_ТЕКСТ_ОТ_МОБИЛКИ")
                .timestamp(expectedTime)
                .status(MessageStatus.SENT)
                .build();

        List<MessageDto> testHistory = List.of(testDto);

        // Мокаем поиск юзера
        when(userRepository.findByUsername("Гарри")).thenReturn(Optional.of(sender));

        // Мокаем сервис — он теперь возвращает DTO!
        when(messageService.getChatHistory(eq(testSenderId), eq(testRecipientId), anyInt(), anyInt()))
                .thenReturn(testHistory);

        // Вызов контроллера
        ResponseEntity<?> response = messageController.getChatHistory(testSenderId, testRecipientId, 0, 20, mockPrincipal);

        assertNotNull(response.getBody());
        List<MessageDto> result = (List<MessageDto>) response.getBody();

        assertAll("Проверка выгрузки истории",
                () -> assertEquals(1, result.size()),
                () -> assertEquals("ЗАШИФРОВАННЫЙ_ТЕКСТ_ОТ_МОБИЛКИ", result.get(0).getContent()),
                () -> assertEquals(expectedTime, result.get(0).getTimestamp()),
                () -> assertEquals(testSenderId, result.get(0).getSenderId())
        );
    }

    @Test
    public void processMessage_ShouldSaveAndSendPayloads() {
        User senderUser = User.builder().id(10L).username("гарри").build();
        User recipientUser = User.builder().id(20L).username("гермиона").build();

        SendMessageRequest req = new SendMessageRequest();
        req.setRecipientId(20L);
        req.setEncryptedForRecipient("ШИФР_ДЛЯ_ГЕРМИОНЫ");
        req.setEncryptedForSender("ШИФР_ДЛЯ_ГАРРИ");

        Principal mockPrincipal = mock(Principal.class);
        when(mockPrincipal.getName()).thenReturn("гарри");

        // 🚀 Создаем сообщение с payloads (как возвращает MessageService)
        Message savedMessage = new Message();
        savedMessage.setId(100L);
        savedMessage.setSender(senderUser);
        savedMessage.setRecipient(recipientUser);
        savedMessage.setTimestamp(LocalDateTime.now());

        MessagePayload payloadForRecipient = MessagePayload.builder()
                .message(savedMessage)
                .recipient(recipientUser)
                .encryptedPayload("ШИФР_ДЛЯ_ГЕРМИОНЫ")
                .status(MessageStatus.SENT)
                .build();

        MessagePayload payloadForSender = MessagePayload.builder()
                .message(savedMessage)
                .recipient(senderUser)
                .encryptedPayload("ШИФР_ДЛЯ_ГАРРИ")
                .status(MessageStatus.READ)
                .build();

        savedMessage.setPayloads(List.of(payloadForRecipient, payloadForSender));

        // Мокаем поиск юзера
        when(userRepository.findByUsername("гарри")).thenReturn(Optional.of(senderUser));
        // Мокаем сервис отправки
        when(messageService.sendMessage(eq(10L), any(SendMessageRequest.class))).thenReturn(savedMessage);

        // Вызываем метод
        messageController.processMessage(req, mockPrincipal);

        // Проверяем, что сервис вызвался
        verify(messageService).sendMessage(eq(10L), any(SendMessageRequest.class));

        // 🚀 Теперь проверяем, что в сокеты ушли MessageDto (2 вызова)
        verify(messagingTemplate, times(2)).convertAndSend(anyString(), any(MessageDto.class));
    }

    @Test
    public void shouldSendPushNotificationWhenMessageArrives() {
        User senderUser = User.builder().id(10L).username("гарри").build();
        User recipientUser = User.builder().id(20L).username("гермиона").build();
        recipientUser.setFcmToken("real_fcm_token_666");

        SendMessageRequest req = new SendMessageRequest();
        req.setRecipientId(20L);
        req.setEncryptedForRecipient("ШИФР");
        req.setEncryptedForSender("ШИФР");
        req.setContentPreview("Привет!"); // Текст для пуша

        Principal mockPrincipal = mock(Principal.class);
        when(mockPrincipal.getName()).thenReturn("гарри");

        Message savedMessage = new Message();
        savedMessage.setId(100L);
        savedMessage.setSender(senderUser);
        savedMessage.setRecipient(recipientUser);
        savedMessage.setTimestamp(LocalDateTime.now());

        // Мокаем поиск юзера (отправителя)
        when(userRepository.findByUsername("гарри")).thenReturn(Optional.of(senderUser));
        // Мокаем сервис отправки
        when(messageService.sendMessage(eq(10L), any(SendMessageRequest.class))).thenReturn(savedMessage);

        // 🚀 ДЕЛАЕМ МОК ЛЕНИВЫМ (lenient), чтобы не падало, если вызов не произошел
        lenient().when(userRepository.findById(20L)).thenReturn(Optional.of(recipientUser));

        // Вызываем метод
        messageController.processMessage(req, mockPrincipal);

        // Проверяем, что пуш вызвался
        verify(pushNotificationService).sendPushNotification(
                eq("real_fcm_token_666"),
                eq("гарри"),
                eq("Привет!"),
                eq(10L),
                eq("гарри")
        );
    }
}