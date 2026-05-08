package com.intellidoc.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
public class UserSyncFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(UserSyncFilter.class);
    public static final String SYNCED_USER_REQUEST_ATTRIBUTE = UserSyncFilter.class.getName() + ".syncedUser";

    private final EntraJwtClaimsMapper claimsMapper;
    private final UserSyncService userSyncService;

    public UserSyncFilter(EntraJwtClaimsMapper claimsMapper, UserSyncService userSyncService) {
        this.claimsMapper = claimsMapper;
        this.userSyncService = userSyncService;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain)
            throws ServletException, IOException {
        Authentication authentication = org.springframework.security.core.context.SecurityContextHolder
                .getContext()
                .getAuthentication();

        if (authentication instanceof JwtAuthenticationToken jwtAuthenticationToken) {
            Jwt jwt = jwtAuthenticationToken.getToken();
            AuthenticatedUser authenticatedUser =
                    claimsMapper.toAuthenticatedUser(jwt, jwtAuthenticationToken.getAuthorities());
            SyncedUserProfile syncedUser = userSyncService.syncAuthenticatedUser(authenticatedUser);
            request.setAttribute(SYNCED_USER_REQUEST_ATTRIBUTE, syncedUser);
            log.debug("Synchronized app_user record for request path {}", request.getRequestURI());
        }

        filterChain.doFilter(request, response);
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        return !path.startsWith("/api/");
    }
}
