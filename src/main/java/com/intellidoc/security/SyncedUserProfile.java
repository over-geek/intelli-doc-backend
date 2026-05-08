package com.intellidoc.security;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record SyncedUserProfile(
        UUID id,
        String entraObjectId,
        String email,
        String displayName,
        String department,
        List<String> roles,
        List<String> groups,
        Instant lastLogin,
        Instant createdAt) {
}
