package com.intellidoc.config;

import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class KeyVaultStartupValidator implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(KeyVaultStartupValidator.class);

    private final IntelliDocProperties properties;
    private final String datasourceUrl;
    private final String datasourceUsername;
    private final String datasourcePassword;
    private final String issuerUri;
    private final String jwkSetUri;
    private final String openAiEndpoint;
    private final String openAiApiKey;

    public KeyVaultStartupValidator(
            IntelliDocProperties properties,
            @Value("${spring.datasource.url:}") String datasourceUrl,
            @Value("${spring.datasource.username:}") String datasourceUsername,
            @Value("${spring.datasource.password:}") String datasourcePassword,
            @Value("${spring.security.oauth2.resourceserver.jwt.issuer-uri:}") String issuerUri,
            @Value("${spring.security.oauth2.resourceserver.jwt.jwk-set-uri:}") String jwkSetUri,
            @Value("${spring.ai.azure.openai.endpoint:}") String openAiEndpoint,
            @Value("${spring.ai.azure.openai.api-key:}") String openAiApiKey) {
        this.properties = properties;
        this.datasourceUrl = datasourceUrl;
        this.datasourceUsername = datasourceUsername;
        this.datasourcePassword = datasourcePassword;
        this.issuerUri = issuerUri;
        this.jwkSetUri = jwkSetUri;
        this.openAiEndpoint = openAiEndpoint;
        this.openAiApiKey = openAiApiKey;
    }

    @Override
    public void run(ApplicationArguments args) {
        validateKeyVaultConfiguration();
        validateResolvedSecrets();
        logResolvedConfiguration();
    }

    private void validateKeyVaultConfiguration() {
        IntelliDocProperties.KeyVault keyVault = properties.getKeyVault();
        if (keyVault.isEnabled() && !StringUtils.hasText(keyVault.getEndpoint())) {
            throw new IllegalStateException("Key Vault integration is enabled but no endpoint is configured.");
        }
    }

    private void validateResolvedSecrets() {
        List<String> missingProperties = new ArrayList<>();

        requireValue("spring.datasource.url", datasourceUrl, missingProperties);
        requireValue("spring.datasource.username", datasourceUsername, missingProperties);
        requireValue("spring.datasource.password", datasourcePassword, missingProperties);
        requireValue("spring.security.oauth2.resourceserver.jwt.issuer-uri", issuerUri, missingProperties);
        requireValue("spring.security.oauth2.resourceserver.jwt.jwk-set-uri", jwkSetUri, missingProperties);
        requireValue("spring.ai.azure.openai.endpoint", openAiEndpoint, missingProperties);
        requireValue("spring.ai.azure.openai.api-key", openAiApiKey, missingProperties);

        if (!missingProperties.isEmpty()) {
            throw new IllegalStateException(
                    "Missing required resolved configuration values: " + String.join(", ", missingProperties)
                            + ". Ensure the values exist in Azure Key Vault or are supplied through standard environment variables.");
        }
    }

    private void logResolvedConfiguration() {
        IntelliDocProperties.KeyVault keyVault = properties.getKeyVault();
        if (keyVault.isEnabled()) {
            log.info(
                    "Azure Key Vault secret loading enabled for endpoint {} with {} expected secret mappings.",
                    keyVault.getEndpoint(),
                    keyVault.getRequiredSecrets().size());
        } else {
            log.warn("Azure Key Vault integration is disabled; relying on environment or imported local property overrides.");
        }
    }

    private void requireValue(String propertyName, String value, List<String> missingProperties) {
        if (!StringUtils.hasText(value)) {
            missingProperties.add(propertyName);
        }
    }
}
