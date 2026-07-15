package com.example.messenger.repository; // 🟢 Проверь свой пакет!

import com.example.messenger.model.Message;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;

public interface MessageRepository extends JpaRepository<Message, Long> {

    // 🚀 БРОНЕБОЙНЫЙ ENTERPRISE-ЗАПРОС НА ЧИСТОМ JPQL:
    // Мы явно объясняем Спрингу: найди переписку, где (отправитель = А и получатель = Б) ИЛИ (отправитель = Б и получатель = А)
    @Query("SELECT m FROM Message m WHERE " +
            "(m.sender.id = :sId AND m.recipient.id = :rId) OR " +
            "(m.sender.id = :rId AND m.recipient.id = :sId) " +
            "ORDER BY m.timestamp ASC")
    List<Message> findChatHistory(@Param("sId") Long senderId, @Param("rId") Long recipientId);
}

