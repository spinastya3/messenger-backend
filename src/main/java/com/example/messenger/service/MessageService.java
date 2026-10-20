package com.example.messenger.service;

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

import java.util.Collections;
import java.util.List;

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

    public List<Message> getChatHistory(Long senderId, Long recipientId, int page, int size) {
        Pageable pageable = PageRequest.of(page, size);
        Long chatId = ChatUtils.generatePrivateChatId(senderId, recipientId);
        List<Message> history = messageRepository.findChatHistory(chatId, pageable);
        Collections.reverse(history);
        return history;
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
                .status(MessageStatus.READ)
                .build();

        payloadRepository.saveAll(List.of(payloadForRecipient, payloadForSender));

        return message;
    }
}