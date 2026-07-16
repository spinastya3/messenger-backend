package com.example.messenger.repository; // 🟢 Проверь свой пакет!

import com.example.messenger.model.Message;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface MessageRepository extends JpaRepository<Message, Long> {

    @Query("SELECT m FROM Message m WHERE " +
            "(m.senderId = :id1 AND m.recipientId = :id2) OR " +
            "(m.senderId = :id2 AND m.recipientId = :id1) " +
            "ORDER BY m.timestamp ASC")
    List<Message> findChatHistory(@Param("id1") long id1, @Param("id2") long id2);

    @Query("SELECT DISTINCT CASE WHEN m.senderId = :userId THEN m.recipientId ELSE m.senderId END " +
            "FROM Message m WHERE m.senderId = :userId OR m.recipientId = :userId")
    List<Long> findActiveChatBuddyIds(@Param("userId") Long userId);
}

