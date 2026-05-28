package com.bone.system.infrastructure.security;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * bone-system 仅解析 JWT，与 bone-iam 共用同一密钥配置前缀 {@code bone.iam.jwt}，
 * 保证两服务在同一部署单元内通过环境变量同时注入。
 */
@Component
@ConfigurationProperties(prefix = "bone.iam.jwt")
@Data
public class SystemJwtProperties {

    /** HS256 对称密钥；必须与 IAM 一致（详设 §5.0）。 */
    private String secretKey = "change-me-change-me-change-me-change-me-32bytes";

    private String tokenPrefix = "Bearer ";

    private String headerName = "Authorization";
}
