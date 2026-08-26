package com.example.messenger;

import com.example.messenger.controller.MessageController;
import com.example.messenger.model.Message;
import com.example.messenger.model.User;
import com.example.messenger.repository.MessageRepository;
import com.example.messenger.repository.UserRepository;
import com.example.messenger.service.MessageService;
import com.example.messenger.service.PushNotificationService;
import com.example.messenger.util.EncryptionUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.AdditionalAnswers.returnsFirstArg;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class MessageControllerTests {


    private MessageController messageController;

    // Оставляем обычные чистые заглушки @Mock для всех зависимостей контроллера
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

    @Mock
    private EncryptionUtil encryptionUtil;

    // 🚀 ШАГ 2: ДОБАВЛЯЕМ СТАНДАРТНЫЙ МЕТОД ИНИЦИАЛИЗАЦИИ ВРУЧНУЮ!
    // Не забудьте импортировать: import org.junit.jupiter.api.BeforeEach;
    @BeforeEach
    public void setUp() {
        // Жестко и принудительно передаем ВСЕ моки в конструктор контроллера по порядку.
        // Теперь ни одно поле (включая messagingTemplate) никогда не будет null!
        messageController = new MessageController(
                messagingTemplate,
                messageRepository,
                userRepository,
                pushNotificationService,
                messageService,
                encryptionUtil
        );
    }

//    @InjectMocks
//    private MessageController messageController;
//
//    @Mock
//    private MessageRepository messageRepository;
//
//    @Mock
//    private UserRepository userRepository;
//
//    @Mock
//    private PushNotificationService pushNotificationService;
//
//    @Mock
//    private MessageService messageService;
//
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

     when(messageService.getChatHistory(eq(testSenderId), eq(testRecipientId), eq(0), eq(20)))
             .thenReturn(testHistory);

     List<Message> result = (List<Message>) messageController.getChatHistory(testSenderId, testRecipientId, 0, 20).getBody();

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

        // 🚀 ОБУЧАЕМ ТОЛЬКО ШИФРОВАЛЬЩИК (Дешифратор стёрли, чтобы Mockito не ругался на UnnecessaryStubbing!)
        when(encryptionUtil.encrypt(eq("гермиона, привет! Пуши работают?")))
                .thenReturn("ENCRYPTED_TEXT");

        // Заглушки репозиториев и баз данных
        when(messageRepository.save(any(Message.class))).then(org.mockito.AdditionalAnswers.returnsFirstArg());
        when(userRepository.findById(eq(20L))).thenReturn(Optional.of(databaseRecipient));
        when(userRepository.findById(eq(10L))).thenReturn(Optional.of(senderUser));

        // 4. Делаем выстрел в контроллер!
        messageController.processMessage(incomingMessage);

        // 5. QA-Проверка
        assertNotNull(incomingMessage.getTimestamp(), "Сообщение успешно обработано сервером");

        // Проверяем, что пуш улетает с КРАСИВЫМ чистым текстом, а не кракозябрами!
        verify(pushNotificationService)
                .sendPushNotification(
                        eq("real_fcm_token_666"),
                        eq("гарри"),
                        eq("гермиона, привет! Пуши работают?"),
                        eq(10L),
                        eq("гарри")
                );
    }
}
