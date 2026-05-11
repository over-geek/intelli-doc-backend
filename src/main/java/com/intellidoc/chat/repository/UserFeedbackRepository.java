package com.intellidoc.chat.repository;

import com.intellidoc.chat.model.UserFeedbackEntity;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface UserFeedbackRepository extends JpaRepository<UserFeedbackEntity, UUID> {

    Optional<UserFeedbackEntity> findByUser_IdAndMessage_Id(UUID userId, UUID messageId);

    @Modifying
    @Query("delete from UserFeedbackEntity feedback where feedback.message.session.id = :sessionId")
    void deleteBySessionId(UUID sessionId);
}
