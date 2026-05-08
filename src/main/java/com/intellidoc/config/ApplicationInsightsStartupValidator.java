package com.intellidoc.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class ApplicationInsightsStartupValidator implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(ApplicationInsightsStartupValidator.class);

    private final IntelliDocProperties properties;

    public ApplicationInsightsStartupValidator(IntelliDocProperties properties) {
        this.properties = properties;
    }

    @Override
    public void run(ApplicationArguments args) {
        IntelliDocProperties.Observability.ApplicationInsights applicationInsights =
                properties.getObservability().getApplicationInsights();

        if (!applicationInsights.isEnabled()) {
            log.warn("Application Insights integration is disabled.");
            return;
        }

        if (!StringUtils.hasText(applicationInsights.getConnectionString())) {
            log.warn(
                    "Application Insights is enabled but APPLICATIONINSIGHTS_CONNECTION_STRING is not set. Telemetry export will remain inactive until that connection string is provided.");
            return;
        }

        log.info(
                "Application Insights enabled for role {} in environment {}.",
                applicationInsights.getRoleName(),
                properties.getObservability().getEnvironment());
    }
}
