package com.example.messenger.controller;

import com.example.messenger.dto.ChatPreviewDto;
import com.example.messenger.model.Message;
import com.example.messenger.model.User;
import com.example.messenger.repository.MessagePayloadRepository;
import com.example.messenger.repository.MessageRepository;
import com.example.messenger.repository.UserRepository;
import com.example.messenger.util.ChatUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@RestController
@RequestMapping("/api/chats")
@RequiredArgsConstructor
public class ChatController {

    private final MessageRepository messageRepository;
    private final MessagePayloadRepository payloadRepository;
    private final UserRepository userRepository;

    @GetMapping("/list")
    public ResponseEntity<?> getChatsList(Principal principal) {
        if (principal == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Не авторизован");
        }

        // 1. Берем текущего юзера из токена
        User currentUser = userRepository.findByUsername(principal.getName())
                .orElseThrow(() -> new IllegalArgumentException("Пользователь не найден"));

        // 2. Получаем список ID всех, с кем переписывались
        List<Long> buddyIds = messageRepository.findActiveChatBuddyIds(currentUser.getId());

        List<ChatPreviewDto> result = new ArrayList<>();

        // 3. Для каждого собеседника собираем инфу
        for (Long buddyId : buddyIds) {
            Optional<User> buddyOpt = userRepository.findById(buddyId);
            if (buddyOpt.isEmpty()) continue;

            User buddy = buddyOpt.get();
            Long chatId = ChatUtils.generatePrivateChatId(currentUser.getId(), buddyId);

            // Ищем последнее сообщение в чате
            Optional<Message> lastMsgOpt = messageRepository.findTopByChatIdOrderByTimestampDesc(chatId);

            String preview = "Нет сообщений";
            java.time.LocalDateTime time = null;

            if (lastMsgOpt.isPresent()) {
                Message lastMsg = lastMsgOpt.get();
                preview = lastMsg.getImageUrl() != null ? "📷 Фотография" : lastMsg.getContentPreview();
                if (preview == null || preview.isEmpty()) preview = "Зашифрованное сообщение";
                time = lastMsg.getTimestamp();
            }

            // Считаем непрочитанные от этого собеседника
            long unread = payloadRepository.countUnread(buddyId, currentUser.getId());

            ChatPreviewDto dto = ChatPreviewDto.builder()
                    .buddyId(buddyId)
                    .buddyName(buddy.getUsername())
                    .lastMessagePreview(preview)
                    .lastMessageTime(time)
                    .unreadCount((int) unread)
                    .build();

            result.add(dto);
        }

        // Сортируем: у кого самое свежее сообщение — тот выше
        result.sort((a, b) -> {
            if (a.getLastMessageTime() == null) return 1;
            if (b.getLastMessageTime() == null) return -1;
            return b.getLastMessageTime().compareTo(a.getLastMessageTime());
        });

        return ResponseEntity.ok(result);
    }
}
