package com.intellidoc.security;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class EntraJwtClaimsMapper {

    private static final Pattern CAMEL_CASE_BOUNDARY = Pattern.compile("(?<=[a-z0-9])(?=[A-Z])");

    public Collection<GrantedAuthority> extractAuthorities(Jwt jwt) {
        Set<String> authorities = new LinkedHashSet<>();

        authorities.addAll(toRoleAuthorities(jwt.getClaimAsStringList("roles")));
        authorities.addAll(toGroupAuthorities(jwt.getClaimAsStringList("groups")));

        return authorities.stream()
                .map(SecurityAuthority::new)
                .sorted(Comparator.comparing(GrantedAuthority::getAuthority))
                .collect(Collectors.toList());
    }

    public AuthenticatedUser toAuthenticatedUser(Jwt jwt, Collection<? extends GrantedAuthority> authorities) {
        List<String> roles = authorities.stream()
                .map(GrantedAuthority::getAuthority)
                .filter(authority -> authority.startsWith("ROLE_"))
                .sorted()
                .toList();

        List<String> groups = authorities.stream()
                .map(GrantedAuthority::getAuthority)
                .filter(authority -> authority.startsWith("GROUP_"))
                .map(authority -> authority.substring("GROUP_".length()))
                .sorted()
                .toList();

        return new AuthenticatedUser(
                firstNonBlank(jwt.getClaimAsString("oid"), jwt.getSubject()),
                firstNonBlank(jwt.getClaimAsString("preferred_username"), jwt.getClaimAsString("email")),
                firstNonBlank(jwt.getClaimAsString("name"), jwt.getClaimAsString("preferred_username")),
                jwt.getClaimAsString("department"),
                roles,
                groups);
    }

    private List<String> toRoleAuthorities(List<String> rawRoles) {
        if (rawRoles == null) {
            return List.of();
        }

        List<String> authorities = new ArrayList<>();
        for (String rawRole : rawRoles) {
            if (!StringUtils.hasText(rawRole)) {
                continue;
            }

            String normalizedRole = rawRole.startsWith("ROLE_")
                    ? rawRole
                    : "ROLE_" + normalizeToken(rawRole);
            authorities.add(normalizedRole);
        }
        return authorities;
    }

    private List<String> toGroupAuthorities(List<String> rawGroups) {
        if (rawGroups == null) {
            return List.of();
        }

        return rawGroups.stream()
                .filter(StringUtils::hasText)
                .map(group -> "GROUP_" + group)
                .toList();
    }

    private String normalizeToken(String value) {
        String separated = CAMEL_CASE_BOUNDARY.matcher(value.trim()).replaceAll("_");
        return separated.replaceAll("[^A-Za-z0-9]+", "_")
                .replaceAll("_+", "_")
                .replaceAll("^_|_$", "")
                .toUpperCase(Locale.ROOT);
    }

    private String firstNonBlank(String... values) {
        return Arrays.stream(values)
                .filter(Objects::nonNull)
                .filter(StringUtils::hasText)
                .findFirst()
                .orElse(null);
    }

    private record SecurityAuthority(String authority) implements GrantedAuthority {

        @Override
        public String getAuthority() {
            return authority;
        }
    }
}
