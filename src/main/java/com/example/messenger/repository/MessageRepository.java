package com.example.messenger.repository;

import com.example.messenger.model.Message;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface MessageRepository extends JpaRepository<Message, Long> {

    // 1. История чата (просто по chatId!)
    @Query("SELECT m FROM Message m WHERE m.chatId = :chatId ORDER BY m.timestamp DESC")
    List<Message> findChatHistory(@Param("chatId") Long chatId, Pageable pageable);

    // 2. Список собеседников для профиля (все чаты, где юзер был отправителем или получателем)
    @Query("SELECT DISTINCT CASE WHEN m.sender.id = :userId THEN m.recipient.id ELSE m.sender.id END " +
            "FROM Message m WHERE m.sender.id = :userId OR m.recipient.id = :userId")
    List<Long> findActiveChatBuddyIds(@Param("userId") Long userId);

    // 3. Поиск фото/видео по имени файла
    @Query("SELECT m FROM Message m WHERE m.imageUrl LIKE %:filename")
    Optional<Message> findByImageUrlEndingWith(@Param("filename") String filename);

    // Найти самое последнее сообщение в чате (для превью)
    Optional<Message> findTopByChatIdOrderByTimestampDesc(Long chatId);
}