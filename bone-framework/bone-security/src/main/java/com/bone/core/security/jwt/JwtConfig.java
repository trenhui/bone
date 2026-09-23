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

  /**
   * RS256 公钥（PEM，X.509）。配置后，消费方在 HS256 验签失败时会回退验 RS256（ADR-0005 双轨过渡）。
   * 仅验证侧需要；各消费模块各自配置同一公钥即可，无需经签发端下发。
   */
  private String rsaPublicKeyPem;

  /** RS256 私钥（PEM，PKCS#8）。仅 IAM 签发端需要；配置后可通过 generateRsaToken 签发 RS256。 */
  private String rsaPrivateKeyPem;

  /** JWKS kid，默认 bone-rsa-1；未来轮换时并行支持多 kid 重叠窗口。 */
  private String rsaKeyId = "bone-rsa-1";

  /** RS256 签发开关：true 时 IAM 改发 RS256（双轨期内仍可被 HS256 消费方接受）。默认 false（维持 HS256）。 */
  private boolean rsaIssuanceEnabled = false;

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
    // RS256 密钥启动期 fail-fast：PEM 非法立即拒启，避免运行期首个 token 才暴露
    if (rsaPublicKeyPem != null && !rsaPublicKeyPem.isBlank()) {
      RsaKeyParser.publicKeyFromPem(rsaPublicKeyPem);
    }
    if (rsaPrivateKeyPem != null && !rsaPrivateKeyPem.isBlank()) {
      RsaKeyParser.privateKeyFromPem(rsaPrivateKeyPem);
      if (!rsaIssuanceEnabled) {
        log.warn("已配置 RS256 私钥但未开启 rsa-issuance-enabled，IAM 仍将签发 HS256");
      }
    }
    if (rsaIssuanceEnabled && (rsaPrivateKeyPem == null || rsaPrivateKeyPem.isBlank())) {
      throw new IllegalStateException(
          "开启 rsa-issuance-enabled 必须同时配置 bone.iam.jwt.rsa-private-key-pem");
    }
  }
}
