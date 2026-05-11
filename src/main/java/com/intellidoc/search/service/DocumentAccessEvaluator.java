package com.intellidoc.search.service;

import com.intellidoc.admin.model.DocumentAccessPolicyEntity;
import com.intellidoc.admin.model.DocumentAccessType;
import com.intellidoc.security.model.AuthenticatedUser;
import java.util.List;
import java.util.Locale;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class DocumentAccessEvaluator {

    public boolean canAccess(AuthenticatedUser user, List<DocumentAccessPolicyEntity> accessPolicies) {
        if (accessPolicies == null || accessPolicies.isEmpty()) {
            return false;
        }

        String normalizedEmail = user.email() == null ? null : user.email().trim().toLowerCase(Locale.ROOT);
        String normalizedDepartment =
                user.department() == null ? null : user.department().trim().toUpperCase(Locale.ROOT);

        for (DocumentAccessPolicyEntity accessPolicy : accessPolicies) {
            if (accessPolicy.getAccessType() == DocumentAccessType.ALL) {
                return true;
            }
            if (accessPolicy.getAccessType() == DocumentAccessType.ROLE
                    && user.roles() != null
                    && user.roles().contains(accessPolicy.getAccessValue())) {
                return true;
            }
            if (accessPolicy.getAccessType() == DocumentAccessType.DEPARTMENT
                    && StringUtils.hasText(normalizedDepartment)
                    && accessPolicy.getAccessValue().equalsIgnoreCase(normalizedDepartment)) {
                return true;
            }
            if (accessPolicy.getAccessType() == DocumentAccessType.USER
                    && StringUtils.hasText(normalizedEmail)
                    && accessPolicy.getAccessValue().equalsIgnoreCase(normalizedEmail)) {
                return true;
            }
        }

        return false;
    }
}
