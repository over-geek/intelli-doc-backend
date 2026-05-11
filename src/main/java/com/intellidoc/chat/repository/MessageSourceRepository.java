package com.intellidoc.chat.repository;

import com.intellidoc.chat.model.MessageSourceEntity;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface MessageSourceRepository extends JpaRepository<MessageSourceEntity, UUID> {

    List<MessageSourceEntity> findByMessage_IdOrderByDisplayOrderAsc(UUID messageId);

    @Modifying
    @Query("delete from MessageSourceEntity source where source.message.session.id = :sessionId")
    void deleteBySessionId(UUID sessionId);
}
