package com.example.messenger.service;

import com.example.messenger.model.Message;
import com.example.messenger.repository.MessageRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

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
            throw new ResponseStatusException(
                    HttpStatus.UNAUTHORIZED, "Пользователь не авторизован");
        }

        String currentUsername = principal.getName();
        Optional<Message> messageOpt = messageRepository.findByImageUrlEndingWith(filename);

        if (messageOpt.isEmpty()) {
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND, "Файл не зарегистрирован в чатах");
        }

        com.example.messenger.model.Message message = messageOpt.get();
        String senderName = message.getSender() != null ? message.getSender().getUsername() : null;
        String recipientName = message.getRecipient() != null ? message.getRecipient().getUsername() : null;

        if (!currentUsername.equals(senderName) && !currentUsername.equals(recipientName)) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN, "У вас нет прав на просмотр этого медиафайла!");
        }
    }
}