package com.intellidoc.gateway;

import com.intellidoc.security.AuthenticatedUser;
import com.intellidoc.security.EntraJwtClaimsMapper;
import com.intellidoc.security.SyncedUserProfile;
import com.intellidoc.security.UserSyncFilter;
import com.intellidoc.security.UserSyncService;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Instant;
import java.util.Collection;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class ApiUserController {

    private final EntraJwtClaimsMapper claimsMapper;
    private final UserSyncService userSyncService;

    public ApiUserController(EntraJwtClaimsMapper claimsMapper, UserSyncService userSyncService) {
        this.claimsMapper = claimsMapper;
        this.userSyncService = userSyncService;
    }

    @GetMapping("/me")
    public ResponseEntity<CurrentUserResponse> currentUser(
            HttpServletRequest request,
            Authentication authentication,
            @AuthenticationPrincipal Jwt jwt) {
        Collection<? extends GrantedAuthority> authorities = authentication.getAuthorities();
        AuthenticatedUser user = claimsMapper.toAuthenticatedUser(jwt, authorities);
        Object syncedUserAttribute = request.getAttribute(UserSyncFilter.SYNCED_USER_REQUEST_ATTRIBUTE);
        SyncedUserProfile syncedUser = syncedUserAttribute instanceof SyncedUserProfile profile
                ? profile
                : userSyncService.syncAuthenticatedUser(user);

        return ResponseEntity.ok(new CurrentUserResponse(
                syncedUser.id(),
                syncedUser.entraObjectId(),
                syncedUser.email(),
                syncedUser.displayName(),
                syncedUser.department(),
                syncedUser.roles(),
                syncedUser.groups(),
                syncedUser.lastLogin(),
                syncedUser.createdAt(),
                jwt.getIssuedAt(),
                jwt.getExpiresAt()));
    }

    @GetMapping("/admin/access-check")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    public ResponseEntity<AccessCheckResponse> adminAccessCheck(Authentication authentication) {
        return ResponseEntity.ok(new AccessCheckResponse(
                "Admin access granted",
                authentication.getAuthorities().stream().map(GrantedAuthority::getAuthority).sorted().toList(),
                Instant.now()));
    }

    public record CurrentUserResponse(
            java.util.UUID userId,
            String entraObjectId,
            String email,
            String displayName,
            String department,
            java.util.List<String> roles,
            java.util.List<String> groups,
            Instant lastLogin,
            Instant createdAt,
            Instant issuedAt,
            Instant expiresAt) {
    }

    public record AccessCheckResponse(String status, java.util.List<String> authorities, Instant timestamp) {
    }
}
