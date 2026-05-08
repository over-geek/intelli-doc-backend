package com.intellidoc.security;

import java.util.List;

public record AuthenticatedUser(
        String userId,
        String email,
        String displayName,
        String department,
        List<String> roles,
        List<String> groups) {
}
