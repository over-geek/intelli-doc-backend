package com.intellidoc.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(IntelliDocProperties.class)
public class IntelliDocConfiguration {
}
