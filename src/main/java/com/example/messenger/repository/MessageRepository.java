package com.example.messenger.repository;
import com.example.messenger.model.MessageDto;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

// Сохраняем переписку сообщений и выгружаем историю чата
@Repository
public interface MessageRepository extends JpaRepository<MessageDto, Long> {

    List<MessageDto> findBySenderIdAndRecipientIdOrSenderIdAndRecipientIdOrderByTimestampAsc(
            Long senderId1, Long recipientId1, Long senderId2, Long recipientId2
    );
}

