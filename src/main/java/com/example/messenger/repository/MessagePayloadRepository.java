package com.example.messenger.repository;

import com.example.messenger.model.MessagePayload;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface MessagePayloadRepository extends JpaRepository<MessagePayload, Long> {

    List<MessagePayload> findAllByMessageId(Long messageId);

    // 4. Непрочитанные (для конкретного получателя)
    @Query("SELECT p FROM MessagePayload p WHERE p.recipient.id = :recipientId " +
            "AND p.message.sender.id = :senderId " +
            "AND (p.status = com.example.messenger.model.MessageStatus.SENT " +
            "OR p.status = com.example.messenger.model.MessageStatus.DELIVERED)")
    List<MessagePayload> findUnreadPayloads(@Param("senderId") Long senderId, @Param("recipientId") Long recipientId);

    // 5. Отправленные (чтобы перевести в ДОСТАВЛЕНО)
    @Query("SELECT p FROM MessagePayload p WHERE p.recipient.id = :recipientId " +
            "AND p.message.sender.id = :senderId " +
            "AND p.status = com.example.messenger.model.MessageStatus.SENT")
    List<MessagePayload> findSentPayloads(@Param("senderId") Long senderId, @Param("recipientId") Long recipientId);

    @Query("SELECT COUNT(p) FROM MessagePayload p WHERE p.recipient.id = :recipientId " +
            "AND p.message.sender.id = :senderId " +
            "AND p.status IN ('SENT', 'DELIVERED')")
    long countUnread(@Param("senderId") Long senderId, @Param("recipientId") Long recipientId);

    @Query("SELECT p FROM MessagePayload p WHERE p.message.id IN :messageIds AND p.recipient.id = :recipientId")
    List<MessagePayload> findPayloadsForUser(@Param("messageIds") List<Long> messageIds, @Param("recipientId") Long recipientId);
}