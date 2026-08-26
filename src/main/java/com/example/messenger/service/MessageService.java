package com.example.messenger.service;

import com.example.messenger.model.Message;
import com.example.messenger.repository.MessageRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;

@Service
public class MessageService {

    @Autowired
    private MessageRepository messageRepository;

    public List<Message> getChatHistory(Long senderId, Long recipientId, int page, int size) {
        Pageable pageable = PageRequest.of(page, size);

        List<Message> history = messageRepository.findChatHistory(senderId, recipientId, pageable);

        Collections.reverse(history);

        return history;
    }
}
