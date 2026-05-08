package com.intellidoc.security;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
public class UserSyncService {

    private static final Logger log = LoggerFactory.getLogger(UserSyncService.class);

    private final AppUserRepository appUserRepository;

    public UserSyncService(AppUserRepository appUserRepository) {
        this.appUserRepository = appUserRepository;
    }

    @Transactional
    public SyncedUserProfile syncAuthenticatedUser(AuthenticatedUser authenticatedUser) {
        validateAuthenticatedUser(authenticatedUser);

        AppUserEntity userEntity = resolveUserEntity(authenticatedUser);
        boolean isNew = userEntity.getId() == null;

        userEntity.setEntraObjectId(authenticatedUser.userId());
        userEntity.setEmail(authenticatedUser.email().trim().toLowerCase());
        userEntity.setDisplayName(authenticatedUser.displayName().trim());
        userEntity.setDepartment(trimToNull(authenticatedUser.department()));
        userEntity.setRoles(sortDistinct(authenticatedUser.roles()));
        userEntity.setLastLogin(Instant.now());

        AppUserEntity savedUser = appUserRepository.save(userEntity);
        if (isNew) {
            log.info("Created app_user record for {}", savedUser.getEmail());
        } else {
            log.debug("Updated app_user record for {}", savedUser.getEmail());
        }

        return toSyncedUserProfile(savedUser, authenticatedUser.groups());
    }

    private AppUserEntity resolveUserEntity(AuthenticatedUser authenticatedUser) {
        Optional<AppUserEntity> byObjectId = appUserRepository.findByEntraObjectId(authenticatedUser.userId());
        if (byObjectId.isPresent()) {
            return byObjectId.get();
        }

        return appUserRepository.findByEmailIgnoreCase(authenticatedUser.email())
                .map(existingUser -> {
                    log.info(
                            "Rebinding app_user record from email {} to Entra object id {}",
                            existingUser.getEmail(),
                            authenticatedUser.userId());
                    return existingUser;
                })
                .orElseGet(AppUserEntity::new);
    }

    private SyncedUserProfile toSyncedUserProfile(AppUserEntity entity, List<String> groups) {
        return new SyncedUserProfile(
                entity.getId(),
                entity.getEntraObjectId(),
                entity.getEmail(),
                entity.getDisplayName(),
                entity.getDepartment(),
                sortDistinct(entity.getRoles()),
                sortDistinct(groups),
                entity.getLastLogin(),
                entity.getCreatedAt());
    }

    private void validateAuthenticatedUser(AuthenticatedUser authenticatedUser) {
        if (!StringUtils.hasText(authenticatedUser.userId())) {
            throw new IllegalArgumentException("Authenticated user is missing the Entra object id.");
        }
        if (!StringUtils.hasText(authenticatedUser.email())) {
            throw new IllegalArgumentException("Authenticated user is missing the email claim.");
        }
        if (!StringUtils.hasText(authenticatedUser.displayName())) {
            throw new IllegalArgumentException("Authenticated user is missing the display name claim.");
        }
    }

    private List<String> sortDistinct(List<String> values) {
        if (values == null) {
            return List.of();
        }

        return values.stream()
                .filter(StringUtils::hasText)
                .map(String::trim)
                .distinct()
                .sorted(Comparator.naturalOrder())
                .toList();
    }

    private String trimToNull(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        return value.trim();
    }
}
