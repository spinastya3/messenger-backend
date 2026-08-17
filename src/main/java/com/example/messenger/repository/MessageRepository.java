package com.example.messenger.repository;

import com.example.messenger.model.Message;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;

public interface MessageRepository extends JpaRepository<Message, Long> {

    @Query("SELECT m FROM Message m WHERE " +
            "(m.sender.id = :sId AND m.recipient.id = :rId) OR " +
            "(m.sender.id = :rId AND m.recipient.id = :sId) " +
            "ORDER BY m.timestamp ASC")
    List<Message> findChatHistory(@Param("sId") Long senderId, @Param("rId") Long recipientId);

    @Query("SELECT DISTINCT CASE WHEN m.sender.id = :userId THEN m.recipient.id ELSE m.sender.id END " +
            "FROM Message m WHERE m.sender.id = :userId OR m.recipient.id = :userId")
    List<Long> findActiveChatBuddyIds(@Param("userId") Long userId);

    // 1. ОБНОВИЛИ: Теперь непрочитанными считаются и SENT, и DELIVERED, и NULL
    @Query("SELECT m FROM Message m WHERE m.sender.id = :sId AND m.recipient.id = :rId " +
            "AND (m.status = com.example.messenger.model.MessageStatus.SENT " +
            "OR m.status = com.example.messenger.model.MessageStatus.DELIVERED " +
            "OR m.status IS NULL)")
    List<Message> findUnreadMessages(@Param("sId") Long senderId, @Param("rId") Long recipientId);

    // 2. ДОБАВИЛИ: Метод для поиска только отправленных сообщений (чтобы перевести их в статус ДОСТАВЛЕНО)
    @Query("SELECT m FROM Message m WHERE m.sender.id = :sId AND m.recipient.id = :rId " +
            "AND (m.status = com.example.messenger.model.MessageStatus.SENT OR m.status IS NULL)")
    List<Message> findSentMessages(@Param("sId") Long senderId, @Param("rId") Long recipientId);
}


