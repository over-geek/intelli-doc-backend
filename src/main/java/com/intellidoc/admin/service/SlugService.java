package com.intellidoc.admin.service;

import java.text.Normalizer;
import java.util.Locale;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class SlugService {

    public String toSlug(String value) {
        String normalized = Normalizer.normalize(value == null ? "" : value, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "");
        String slug = normalized
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", "-")
                .replaceAll("(^-+|-+$)", "");
        return StringUtils.hasText(slug) ? slug : "document";
    }
}
