package com.intellidoc.search.service;

import com.intellidoc.security.model.AuthenticatedUser;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class SearchSecurityFilterBuilder {

    public String buildFilter(AuthenticatedUser user) {
        List<String> accessClauses = new ArrayList<>();
        accessClauses.add("allowed_roles/any(r: r eq 'ALL')");

        if (user.roles() != null) {
            for (String role : user.roles()) {
                if (StringUtils.hasText(role)) {
                    accessClauses.add("allowed_roles/any(r: r eq '%s')".formatted(escape(role.trim())));
                }
            }
        }

        if (StringUtils.hasText(user.department())) {
            accessClauses.add("allowed_departments/any(d: d eq '%s')"
                    .formatted(escape(user.department().trim().toUpperCase(Locale.ROOT))));
        }

        if (StringUtils.hasText(user.email())) {
            accessClauses.add("allowed_users/any(u: u eq '%s')"
                    .formatted(escape(user.email().trim().toLowerCase(Locale.ROOT))));
        }

        return "status eq 'PUBLISHED' and (%s)".formatted(String.join(" or ", accessClauses));
    }

    private String escape(String value) {
        return value.replace("'", "''");
    }
}
