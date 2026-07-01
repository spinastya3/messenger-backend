package com.example.messenger;

import com.example.messenger.controller.MessageController;
import com.example.messenger.model.MessageDto;
import com.example.messenger.repository.MessageRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.AdditionalAnswers.returnsFirstArg;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class MessageControllerTests {


    @InjectMocks
    private MessageController messageController;

    @Mock
    private MessageRepository messageRepository;

    @Mock
    private SimpMessagingTemplate messagingTemplate;

    MessageDto testMessage = new MessageDto();

 @Test
 public void chatHistoryTest() {

     long testSenderId = 1L;
     long testRecipientId = 2L;
     LocalDateTime expectedTime = LocalDateTime.of(2026, 6, 30, 14, 0, 0);


     MessageDto testMessage = new MessageDto();
     testMessage.setId(555L);
     testMessage.setContent("Проверка связи");
     testMessage.setTimestamp(expectedTime);

     List<MessageDto> testHistory = List.of(testMessage);

     when(messageRepository
             .findBySenderIdAndRecipientIdOrSenderIdAndRecipientIdOrderByTimestampAsc(testSenderId, testRecipientId, testRecipientId, testSenderId))
             .thenReturn(testHistory);

     List<MessageDto> result = messageController.getChatHistory(testSenderId, testRecipientId).getBody();

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
        when(messageRepository.save(any(MessageDto.class))).then(returnsFirstArg());
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
}
