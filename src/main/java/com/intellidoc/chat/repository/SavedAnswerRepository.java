package com.intellidoc.chat.repository;

import com.intellidoc.chat.model.SavedAnswerEntity;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface SavedAnswerRepository extends JpaRepository<SavedAnswerEntity, UUID> {

    Optional<SavedAnswerEntity> findByUser_IdAndMessage_Id(UUID userId, UUID messageId);

    List<SavedAnswerEntity> findByUser_IdOrderByCreatedAtDesc(UUID userId);

    @Modifying
    @Query("delete from SavedAnswerEntity savedAnswer where savedAnswer.message.session.id = :sessionId")
    void deleteBySessionId(UUID sessionId);
}
