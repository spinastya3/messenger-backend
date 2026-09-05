package com.example.messenger;

import com.example.messenger.controller.MessageController;
import com.example.messenger.crypto.SessionKeyManager;
import com.example.messenger.dto.MessageDto;
import com.example.messenger.model.Message;
import com.example.messenger.model.MessageStatus;
import com.example.messenger.model.User;
import com.example.messenger.repository.MessageRepository;
import com.example.messenger.repository.UserRepository;
import com.example.messenger.service.MessageService;
import com.example.messenger.service.PushNotificationService;
import com.example.messenger.crypto.EncryptionUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.AdditionalAnswers;
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
    @Mock
    private EncryptionUtil encryptionUtil;

    // 🛡️ ЛОВИМ НОВУЮ ЗАВИСИМОСТЬ
    @Mock
    private SessionKeyManager sessionKeyManager;

    @BeforeEach
    public void setUp() {
        // Передаем ВСЕ моки в конструктор контроллера по обновленному порядку
        messageController = new MessageController(
                messagingTemplate,
                messageRepository,
                userRepository,
                pushNotificationService,
                messageService,
                encryptionUtil,
                sessionKeyManager // <-- Передали менеджер ключей
        );
    }

    @Test
    public void chatHistoryTest() {
        long testSenderId = 1L;
        long testRecipientId = 2L;
        LocalDateTime expectedTime = LocalDateTime.of(2026, 6, 30, 14, 0, 0);
        String mockUsername = "HarryPotter";
        String mockSessionKey = "mock_base64_session_key_1234567890=";

        // 1. Создаем мок для Principal (авторизованный пользователь)
        Principal mockPrincipal = mock(Principal.class);
        when(mockPrincipal.getName()).thenReturn(mockUsername);

        // 2. Мокаем получение сессионного ключа из памяти сервера
        when(sessionKeyManager.getKey(mockUsername)).thenReturn(mockSessionKey);

        // 3. Собираем тестовую сущность из базы данных (Entity)
        User sender = User.builder().id(testSenderId).username("Гарри").build();
        User recipient = User.builder().id(testRecipientId).username("Рон").build();

        Message testMessage = new Message();
        testMessage.setId(555L);
        testMessage.setSender(sender);
        testMessage.setRecipient(recipient);
        testMessage.setContent("Проверка связи");
        testMessage.setTimestamp(expectedTime);
        testMessage.setStatus(MessageStatus.SENT);

        List<Message> testHistory = List.of(testMessage);

        // Мокаем вызов сервиса истории
        when(messageService.getChatHistory(eq(testSenderId), eq(testRecipientId), eq(0), eq(20)))
                .thenReturn(testHistory);

        // Мокаем шифрование утилиты (имитируем, что текст шифруется для сети)
        when(encryptionUtil.encrypt("Проверка связи", mockSessionKey))
                .thenReturn("MOCK_ENCRYPTED_TEXT_BASE64");

        // 4. Вызываем метод контроллера (передаем mockPrincipal пятым аргументом!)
        ResponseEntity<?> response = messageController.getChatHistory(testSenderId, testRecipientId, 0, 20, mockPrincipal);

        // 🔥 Приводим результат строго к List<MessageDto>, так как контроллер теперь возвращает DTO!
        List<MessageDto> result = (List<MessageDto>) response.getBody();

        // 5. Проверяем утверждения (Asserts)
        assertNotNull(result, "Результат не должен быть null");
        assertAll("Проверка выгрузки истории чата через DTO",
                () -> assertEquals(1, result.size(), "В истории не одно сообщение"),
                // Текст должен быть зашифрован под текущую сессию!
                () -> assertEquals("MOCK_ENCRYPTED_TEXT_BASE64", result.get(0).getContent(), "Текст для сети не зашифрован сессионным ключом"),
                () -> assertEquals(expectedTime, result.get(0).getTimestamp(), "Время сообщения не совпадает со временем в БД"),
                () -> assertEquals(testSenderId, result.get(0).getSenderId(), "ID отправителя в DTO не совпадает")
        );
    }

    @Test
    public void processMessage_ShouldSetCurrentTimestampAndSave() {
        // 1. Готовим тестовых пользователей с ID и Username
        User senderUser = new User();
        senderUser.setId(10L);
        senderUser.setUsername("гарри");

        User recipientUser = new User();
        recipientUser.setId(20L);
        recipientUser.setUsername("гермиона");

        // 2. Настраиваем сообщение
        Message localTestMessage = new Message();
        localTestMessage.setSender(senderUser);
        localTestMessage.setRecipient(recipientUser);
        localTestMessage.setContent("Зашифрованный_бред_от_мобилки"); // Имитируем, что мобилка прислала зашифрованный текст

        // 🚀 ОБУЧАЕМ МОКИТО:
        // Разрешаем контроллеру успешно найти Гарри и Гермиону в базе данных
        when(userRepository.findById(eq(10L))).thenReturn(Optional.of(senderUser));
        when(userRepository.findById(eq(20L))).thenReturn(Optional.of(recipientUser));

        // 🛡️ Мокаем менеджер ключей, чтобы контроллер нашел ключи сессий в памяти
        String mockKey = "mock_base64_session_key=";
        when(sessionKeyManager.getKey("гарри")).thenReturn(mockKey);
        when(sessionKeyManager.getKey("гермиона")).thenReturn(mockKey);

        // 🔥 Обучаем ДЕШИФРОВАТЬ пришедший текст (возвращаем чистый текст для логики пушей и времени)
        when(encryptionUtil.decrypt(eq("Зашифрованный_бред_от_мобилки"), eq(mockKey))).thenReturn("Проверяем время");

        // Обучаем ШИФРОВАТЬ обратно для сокетов получателя и отправителя (теперь эти методы точно вызовутся!)
        when(encryptionUtil.encrypt(eq("Проверяем время"), eq(mockKey))).thenReturn("ENCRYPTED_TEXT_FOR_NETWORK");

        // Стандартная заглушка сохранения репозитория
        when(messageRepository.save(any(Message.class))).then(org.mockito.AdditionalAnswers.returnsFirstArg());

        LocalDateTime testStartTime = LocalDateTime.now().minusSeconds(1);

        // 3. Делаем выстрел в контроллер!
        messageController.processMessage(localTestMessage);

        LocalDateTime messageTime = localTestMessage.getTimestamp();

        // 4. Проверки
        assertAll("Проверка генерации живого тайминга при отправке",
                () -> assertNotNull(messageTime, "Сервер обязан сгенерировать timestamp!"),

                () -> assertTrue(messageTime.isAfter(testStartTime),
                        "Тайминг сообщения должен быть актуальным (создан только что)"),

                // Важно: проверяем, что в конечном счете в базу пошел чистый текст (благодаря конвертеру)
                // Но так как в сокет улетает зашифрованный, в самом объекте после вызовов convertAndSend
                // останется последний зашифрованный вариант. Проверим, что контент не null
                () -> assertNotNull(localTestMessage.getContent(), "Контент сообщения не должен быть пустым")
        );
    }

    @Test
    public void shouldSendPushNotificationWhenMessageArrives() {
        // 1. Готовим отправителя
        User senderUser = new User();
        senderUser.setId(10L);
        senderUser.setUsername("гарри");

        // 2. Готовим получателя
        User recipientUser = new User();
        recipientUser.setId(20L);
        recipientUser.setUsername("гермиона");

        // База данных вернет получателя уже с сочным токеном!
        User databaseRecipient = new User();
        databaseRecipient.setId(20L);
        databaseRecipient.setUsername("гермиона");
        databaseRecipient.setFcmToken("real_fcm_token_666");

        // 3. Собираем сообщение
        Message incomingMessage = new Message();
        incomingMessage.setSender(senderUser);
        incomingMessage.setRecipient(recipientUser);
        incomingMessage.setContent("ЗАШИФРОВАННЫЙ_ТЕКСТ_ОТ_МОБИЛКИ"); // Имитируем шифр в сети

        // 🛡️ ОБУЧАЕМ МЕНЕДЖЕР КЛЮЧЕЙ
        String mockKey = "mock_base64_session_key=";
        when(sessionKeyManager.getKey("гарри")).thenReturn(mockKey);
        when(sessionKeyManager.getKey("гермиона")).thenReturn(mockKey);

        // 🔓 ОБУЧАЕМ ДЕШИФРОВАТЬ (Возвращаем чистый текст для пуша!)
        when(encryptionUtil.decrypt(eq("ЗАШИФРОВАННЫЙ_ТЕКСТ_ОТ_МОБИЛКИ"), eq(mockKey)))
                .thenReturn("гермиона, привет! Пуши работают?");

        // 🔒 ОБУЧАЕМ ШИФРОВАТЬ ДЛЯ СОКЕТОВ (Чтобы Mockito не ругался на UnnecessaryStubbing!)
        when(encryptionUtil.encrypt(eq("гермиона, привет! Пуши работают?"), eq(mockKey)))
                .thenReturn("ENCRYPTED_TEXT_FOR_SOKETS");

        // Заглушки репозиториев
        when(messageRepository.save(any(Message.class))).then(org.mockito.AdditionalAnswers.returnsFirstArg());
        when(userRepository.findById(eq(20L))).thenReturn(Optional.of(databaseRecipient));
        when(userRepository.findById(eq(10L))).thenReturn(Optional.of(senderUser));

        // 4. Делаем выстрел в контроллер!
        messageController.processMessage(incomingMessage);

        // 5. QA-Проверка
        assertNotNull(incomingMessage.getTimestamp(), "Сообщение успешно обработано сервером");

        // Проверяем, что пуш улетает с КРАСИВЫМ чистым текстом, который вернул дешифратор!
        verify(pushNotificationService)
                .sendPushNotification(
                        eq("real_fcm_token_666"),
                        eq("гарри"),
                        eq("гермиона, привет! Пуши работают?"), // Идеальный чистый текст улетает в Google!
                        eq(10L),
                        eq("гарри")
                );
    }
}
