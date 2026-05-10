package com.intellidoc.ingestion.service;

import com.intellidoc.admin.repository.DocumentRepository;
import com.intellidoc.ingestion.model.IngestionQueueMessage;
import com.intellidoc.notification.model.NotificationChannel;
import com.intellidoc.notification.model.NotificationEntity;
import com.intellidoc.notification.model.NotificationType;
import com.intellidoc.notification.repository.NotificationRepository;
import com.intellidoc.security.repository.AppUserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AdminIngestionFailureNotificationService {

    private static final Logger log = LoggerFactory.getLogger(AdminIngestionFailureNotificationService.class);

    private final AppUserRepository appUserRepository;
    private final DocumentRepository documentRepository;
    private final NotificationRepository notificationRepository;

    public AdminIngestionFailureNotificationService(
            AppUserRepository appUserRepository,
            DocumentRepository documentRepository,
            NotificationRepository notificationRepository) {
        this.appUserRepository = appUserRepository;
        this.documentRepository = documentRepository;
        this.notificationRepository = notificationRepository;
    }

    @Transactional
    public void notifyAdmins(IngestionQueueMessage message, String failureReason) {
        documentRepository.findById(message.documentId()).ifPresent(document -> {
            var admins = appUserRepository.findAdminUsers();
            for (var admin : admins) {
                NotificationEntity notification = new NotificationEntity();
                notification.setUser(admin);
                notification.setDocument(document);
                notification.setType(NotificationType.POLICY_UPDATED);
                notification.setTitle("Document ingestion failed");
                notification.setBody(
                        "Ingestion failed for document '%s' version %s. Reason: %s"
                                .formatted(document.getTitle(), message.versionNumber(), failureReason));
                notification.setChannel(NotificationChannel.IN_APP);
                notification.setRead(false);
                notificationRepository.save(notification);
            }
            log.info(
                    "Created {} admin ingestion failure notifications for document {} version {}.",
                    admins.size(),
                    message.documentId(),
                    message.versionNumber());
        });
    }
}
