package com.example.messenger.service;

import com.example.messenger.model.Message;
import com.example.messenger.repository.MessageRepository;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

import java.security.Principal;
import java.util.Optional;

@Service
public class FileSecurityService {

    private final MessageRepository messageRepository;

    public FileSecurityService(MessageRepository messageRepository) {
        this.messageRepository = messageRepository;
    }

    public void checkFileAccess(String filename, Principal principal) {
        if (principal == null) {
            throw new org.springframework.web.server.ResponseStatusException(
                    org.springframework.http.HttpStatus.UNAUTHORIZED, "Пользователь не авторизован");
        }

        String currentUsername = principal.getName();
        java.util.Optional<com.example.messenger.model.Message> messageOpt = messageRepository.findByImageUrlEndingWith(filename);

        if (messageOpt.isEmpty()) {
            throw new org.springframework.web.server.ResponseStatusException(
                    org.springframework.http.HttpStatus.NOT_FOUND, "Файл не зарегистрирован в чатах");
        }

        com.example.messenger.model.Message message = messageOpt.get();
        String senderName = message.getSender() != null ? message.getSender().getUsername() : null;
        String recipientName = message.getRecipient() != null ? message.getRecipient().getUsername() : null;

        if (!currentUsername.equals(senderName) && !currentUsername.equals(recipientName)) {
            throw new org.springframework.web.server.ResponseStatusException(
                    org.springframework.http.HttpStatus.FORBIDDEN, "У вас нет прав на просмотр этого медиафайла!");
        }
    }
}