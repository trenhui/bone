package com.bone.integration.infrastructure.config;

import com.bone.integration.application.config.IntegrationOutboxProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(IntegrationOutboxProperties.class)
public class IntegrationOutboxConfig {}
