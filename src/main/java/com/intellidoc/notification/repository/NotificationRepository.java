package com.intellidoc.notification.repository;

import com.intellidoc.notification.model.NotificationEntity;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface NotificationRepository extends JpaRepository<NotificationEntity, UUID> {
}
