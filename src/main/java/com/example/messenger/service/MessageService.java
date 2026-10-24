package com.example.messenger.service;

import com.example.messenger.dto.MessageDto;
import com.example.messenger.dto.SendMessageRequest;
import com.example.messenger.model.*;
import com.example.messenger.repository.ChatRepository;
import com.example.messenger.repository.MessagePayloadRepository;
import com.example.messenger.repository.MessageRepository;
import com.example.messenger.repository.UserRepository;
import com.example.messenger.util.ChatUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class MessageService {

    @Autowired
    private MessageRepository messageRepository;

    @Autowired
    private MessagePayloadRepository payloadRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ChatRepository chatRepository;

    @Transactional(readOnly = true) // 🚀 Важно! Транзакция должна быть открыта
    public List<MessageDto> getChatHistory(Long currentUserId, Long recipientId, int page, int size) {
        Pageable pageable = PageRequest.of(page, size);
        Long chatId = ChatUtils.generatePrivateChatId(currentUserId, recipientId);

        // 1. Достаем сообщения (без payloads)
        List<Message> messages = messageRepository.findChatHistory(chatId, pageable);
        Collections.reverse(messages);

        if (messages.isEmpty()) {
            return new ArrayList<>();
        }

        // 2. Достаем payloads ТОЛЬКО для текущего пользователя
        List<Long> messageIds = messages.stream().map(Message::getId).toList();
        List<MessagePayload> payloads = payloadRepository.findPayloadsForUser(messageIds, currentUserId);

        // 3. Группируем payloads по messageId для быстрого доступа
        Map<Long, MessagePayload> payloadMap = payloads.stream()
                .collect(Collectors.toMap(p -> p.getMessage().getId(), p -> p));

        // 4. Собираем DTO
        List<MessageDto> result = new ArrayList<>();
        for (Message msg : messages) {
            MessagePayload payload = payloadMap.get(msg.getId());

            result.add(MessageDto.builder()
                    .id(msg.getId())
                    .senderId(msg.getSenderId())
                    .senderName(msg.getSenderName())
                    .recipientId(msg.getRecipientId())
                    .content(payload != null ? payload.getEncryptedPayload() : null)
                    .timestamp(msg.getTimestamp())
                    .imageUrl(msg.getImageUrl())
                    .status(payload != null ? payload.getStatus() : MessageStatus.SENT)
                    .build());
        }

        return result;
    }

    @Transactional
    public Message sendMessage(Long senderId, SendMessageRequest req) {
        User sender = userRepository.findById(senderId)
                .orElseThrow(() -> new IllegalArgumentException("Отправитель не найден"));
        User recipient = userRepository.findById(req.getRecipientId())
                .orElseThrow(() -> new IllegalArgumentException("Получатель не найден"));

        Long chatId = ChatUtils.generatePrivateChatId(senderId, recipient.getId());

        // Создаем чат, если его еще нет в БД
        chatRepository.findById(chatId).orElseGet(() -> {
            Chat newChat = Chat.builder()
                    .id(chatId)
                    .type("PRIVATE")
                    .build();
            return chatRepository.save(newChat);
        });

        Message message = Message.builder()
                .chatId(chatId)
                .sender(sender)
                .recipient(recipient)
                .contentPreview(req.getContentPreview())
                .imageUrl(req.getImageUrl())
                .build();
        message = messageRepository.save(message);

        MessagePayload payloadForRecipient = MessagePayload.builder()
                .message(message)
                .recipient(recipient)
                .encryptedPayload(req.getEncryptedForRecipient())
                .status(MessageStatus.SENT)
                .build();

        MessagePayload payloadForSender = MessagePayload.builder()
                .message(message)
                .recipient(sender)
                .encryptedPayload(req.getEncryptedForSender())
                .status(MessageStatus.SENT)
                .build();

        payloadRepository.saveAll(List.of(payloadForRecipient, payloadForSender));

        message.setPayloads(List.of(payloadForRecipient, payloadForSender));

        return message;
    }
}