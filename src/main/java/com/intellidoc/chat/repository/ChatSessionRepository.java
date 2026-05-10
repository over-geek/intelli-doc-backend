package com.intellidoc.chat.repository;

import com.intellidoc.chat.model.ChatSessionEntity;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ChatSessionRepository extends JpaRepository<ChatSessionEntity, UUID> {

    List<ChatSessionEntity> findByUser_IdOrderByLastMessageAtDesc(UUID userId);

    Optional<ChatSessionEntity> findByIdAndUser_Id(UUID id, UUID userId);
}
