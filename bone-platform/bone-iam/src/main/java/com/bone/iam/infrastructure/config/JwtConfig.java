package com.bone.iam.infrastructure.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConfigurationProperties(prefix = "bone.iam.jwt")
@Data
public class JwtConfig {
    /**
     * HS256/HS384/HS512 对称密钥，建议 >= 32 字节（生产环境务必外部化）。
     */
    private String secretKey = "change-me-change-me-change-me-change-me-32bytes";

    /**
     * Access Token 过期时间（毫秒），默认 2 小时。
     */
    private long expirationMs = 2 * 60 * 60 * 1000L;

    /**
     * Refresh Token 过期时间（毫秒），默认 7 天（当前版本先预留）。
     */
    private long refreshExpirationMs = 7 * 24 * 60 * 60 * 1000L;

    private String tokenPrefix = "Bearer ";
    private String headerName = "Authorization";
}