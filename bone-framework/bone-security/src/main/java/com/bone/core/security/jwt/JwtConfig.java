package com.bone.core.security.jwt;

import jakarta.annotation.PostConstruct;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.Profiles;

/** JWT 配置。所有消费 JWT 的模块共用此配置。 */
@Slf4j
@Configuration
@ConfigurationProperties(prefix = "bone.iam.jwt")
@Data
public class JwtConfig {
  private static final String DEFAULT_SECRET_PREFIX = "change-me";

  private final ConfigurableEnvironment environment;

  /** HS256/HS384/HS512 对称密钥，建议 >= 32 字节（生产环境务必外部化）。 */
  private String secretKey = "change-me-change-me-change-me-change-me-32bytes";

  /** Access Token 过期时间（毫秒），默认 2 小时。 */
  private long expirationMs = 2 * 60 * 60 * 1000L;

  /** Refresh Token 过期时间（毫秒），默认 7 天。 */
  private long refreshExpirationMs = 7 * 24 * 60 * 60 * 1000L;

  private String tokenPrefix = "Bearer ";
  private String headerName = "Authorization";

  @PostConstruct
  public void validate() {
    if (secretKey == null || secretKey.isBlank()) {
      throw new IllegalStateException("bone.iam.jwt.secret-key 不能为空");
    }
    if (secretKey.length() < 32) {
      throw new IllegalStateException(
          "bone.iam.jwt.secret-key 长度必须 >= 32 字节，当前长度: " + secretKey.length());
    }
    if (secretKey.startsWith(DEFAULT_SECRET_PREFIX)
        && environment.acceptsProfiles(Profiles.of("prod"))) {
      throw new IllegalStateException("生产环境禁止使用默认 JWT 密钥，请设置 BONE_IAM_JWT_SECRET_KEY 环境变量");
    }
    if (secretKey.startsWith(DEFAULT_SECRET_PREFIX)) {
      log.warn("⚠️ 使用默认 JWT 密钥，仅限开发环境，生产环境将拒绝启动");
    }
  }
}
