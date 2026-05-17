package com.bone.integration.infrastructure.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(IntegrationOutboxProperties.class)
public class IntegrationOutboxConfig {}
