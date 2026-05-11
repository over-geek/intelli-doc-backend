package com.intellidoc.chat.repository;

import com.intellidoc.chat.model.ChatMessageEntity;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;

public interface ChatMessageRepository extends JpaRepository<ChatMessageEntity, UUID> {

    List<ChatMessageEntity> findBySession_IdOrderByCreatedAtAsc(UUID sessionId);

    List<ChatMessageEntity> findTop5BySession_IdOrderByCreatedAtDesc(UUID sessionId);

    java.util.Optional<ChatMessageEntity> findByIdAndSession_User_Id(UUID id, UUID userId);

    @Modifying
    void deleteBySession_Id(UUID sessionId);
}
