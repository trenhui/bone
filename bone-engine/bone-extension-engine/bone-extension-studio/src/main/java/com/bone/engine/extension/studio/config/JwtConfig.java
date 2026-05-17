package com.bone.engine.extension.studio.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * 与 bone-iam 共用同一 JWT 配置前缀，便于 Shell / 微应用携带同一 Bearer Token。
 */
@Configuration
@ConfigurationProperties(prefix = "bone.iam.jwt")
@Data
public class JwtConfig {
    private String secretKey = "change-me-change-me-change-me-change-me-32bytes";
    private long expirationMs = 2 * 60 * 60 * 1000L;
    private String tokenPrefix = "Bearer ";
    private String headerName = "Authorization";
}
