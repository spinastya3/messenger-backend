package com.example.messenger;

import com.example.messenger.controller.MessageController;
import com.example.messenger.model.Message;
import com.example.messenger.model.User;
import com.example.messenger.repository.MessageRepository;
import com.example.messenger.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.AdditionalAnswers.returnsFirstArg;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class MessageControllerTests {


    @InjectMocks
    private MessageController messageController;

    @Mock
    private MessageRepository messageRepository;

    @Mock
    private SimpMessagingTemplate messagingTemplate;

    @Mock
    private UserRepository userRepository; // 🟢 Заглушка для базы юзеров

    @Mock
    private PushNotificationService pushNotificationService; // 🟢 Заглушка для пушей

    Message testMessage = new Message();

 @Test
 public void chatHistoryTest() {

     long testSenderId = 1L;
     long testRecipientId = 2L;
     LocalDateTime expectedTime = LocalDateTime.of(2026, 6, 30, 14, 0, 0);


     Message testMessage = new Message();
     testMessage.setId(555L);
     testMessage.setContent("Проверка связи");
     testMessage.setTimestamp(expectedTime);

     List<Message> testHistory = List.of(testMessage);

     when(messageRepository
             .findBySenderIdAndRecipientIdOrSenderIdAndRecipientIdOrderByTimestampAsc(testSenderId, testRecipientId, testRecipientId, testSenderId))
             .thenReturn(testHistory);

     List<Message> result = messageController.getChatHistory(testSenderId, testRecipientId).getBody();

     assertAll("Проверка выгрузки истории чата",
             () -> assertEquals(1, result.size(), "В истории не одно сообщение"),
             () -> assertEquals("Проверка связи", result.getFirst().getContent(), "Текст не совпадает"),
             () -> assertEquals(expectedTime, result.getFirst().getTimestamp(), "Время сообщения не совпадает со временем в БД")
     );

 }

    @Test
    public void processMessage_ShouldSetCurrentTimestampAndSave() {

        testMessage.setContent("Проверяем время");

        LocalDateTime testStartTime = java.time.LocalDateTime.now().minusSeconds(1);
        when(messageRepository.save(any(Message.class))).then(returnsFirstArg());
        messageController.processMessage(testMessage);

        LocalDateTime messageTime = testMessage.getTimestamp();

        assertAll("Проверка генерации живого тайминга при отправке",
                // 🕵️‍♂️ Проверяем, что поле времени вообще заполнилось
                () -> assertNotNull(messageTime, "Сервер обязан сгенерировать timestamp!"),

                // 🕵️‍♂️ Проверяем диапазон: время сообщения должно быть СТРОГО позже, чем время старта теста!
                () -> assertTrue(messageTime.isAfter(testStartTime),
                        "Тайминг сообщения должен быть актуальным (создан только что)"),

                // 🕵️‍♂️ Проверяем диапазон: время сообщения не должно улететь в далекое будущее
                () -> assertTrue(messageTime.isBefore(java.time.LocalDateTime.now().plusSeconds(1)),
                        "Тайминг сообщения не должен превышать текущее время")
        );
    }
    @Test
    public void shouldSendPushNotificationWhenMessageArrives() {
        // 1. Готовим отправителя
        User senderUser = new User();
        senderUser.setId(10L);
        senderUser.setUsername("гарри");

        // 2. Готовим получателя (у которого в базе ХРАНИТСЯ ТОКЕН!)
        User recipientUser = new User();
        recipientUser.setId(20L);
        recipientUser.setUsername("гермиона");

        // База данных вернет нам маму уже с сочным токеном!
        User databaseRecipient = new User();
        databaseRecipient.setId(20L);
        databaseRecipient.setUsername("гермиона");
        databaseRecipient.setFcmToken("real_fcm_token_666");

        // 3. Собираем сообщение
        Message incomingMessage = new Message();
        incomingMessage.setSender(senderUser);
        incomingMessage.setRecipient(recipientUser);
        incomingMessage.setContent("гермиона, привет! Пуши работают?");

        // 4. Обучаем Мокито
        when(messageRepository.save(any(Message.class))).then(returnsFirstArg());
        when(userRepository.findById(20L)).thenReturn(Optional.of(databaseRecipient));
        when(userRepository.findById(10L)).thenReturn(Optional.of(senderUser));

        // 5. Делаем выстрел в контроллер!
        messageController.processMessage(incomingMessage);

        // 6. QA-Проверка: если тест дошел до конца и не выкинул ошибок —
        // значит, вся цепочка поиска токена и вызова сервиса Firebase отработала штатно!
        assertNotNull(incomingMessage.getTimestamp(), "Сообщение успешно обработано сервером");
        verify(pushNotificationService)
                .sendPushNotification("real_fcm_token_666", "Новое сообщение", "гермиона, привет! Пуши работают?", 10L, "гарри");    }
}
