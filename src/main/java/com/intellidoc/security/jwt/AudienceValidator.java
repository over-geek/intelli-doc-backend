package com.intellidoc.security.jwt;

import java.util.List;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.util.StringUtils;

public class AudienceValidator implements OAuth2TokenValidator<Jwt> {

    private final String requiredAudience;

    public AudienceValidator(String requiredAudience) {
        this.requiredAudience = requiredAudience;
    }

    @Override
    public OAuth2TokenValidatorResult validate(Jwt token) {
        if (!StringUtils.hasText(requiredAudience)) {
            return OAuth2TokenValidatorResult.success();
        }

        List<String> audiences = token.getAudience();
        if (audiences.contains(requiredAudience)) {
            return OAuth2TokenValidatorResult.success();
        }

        return OAuth2TokenValidatorResult.failure(new OAuth2Error(
                "invalid_token",
                "The required audience '" + requiredAudience + "' is missing from the token.",
                null));
    }
}
