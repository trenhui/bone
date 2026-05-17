package com.bone.integration.infrastructure.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConfigurationProperties(prefix = "bone.integration.security")
@Data
public class IntegrationSecurityProperties {
    /**
     * 为 true 时 {@code /integration/**} 需 IAM JWT；开发默认 false。
     */
    private boolean jwtEnabled = false;
}
