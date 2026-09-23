package com.bone.gateway.config;

import jakarta.annotation.PostConstruct;
import java.util.ArrayList;
import java.util.List;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.Profiles;

/** 网关 JWT 鉴权配置。密钥须与下游签发服务共享（BONE_IAM_JWT_SECRET_KEY / BONE_JWT_SECRET 注入）。 */
@Slf4j
@Data
@Configuration
@ConfigurationProperties(prefix = "bone.gateway.jwt")
public class GatewayJwtProperties {

  private static final String DEFAULT_SECRET = "change-me-change-me-change-me-change-me-32bytes";
  private static final String DEV_FALLBACK_SECRET = "dev-only-secret-key-minimum-32-bytes-long";

  @Autowired private ConfigurableEnvironment environment;

  /** HS256 对称密钥，须与 bone-iam 等签发模块一致（生产环境必须外部化，禁止默认密钥）。 */
  private String secretKey;

  /** 启动期 fail-fast：与 bone-security JwtConfig 对齐，禁止空/短/默认密钥，prod 环境直接拒绝启动。 */
  @PostConstruct
  public void validate() {
    if (secretKey == null || secretKey.isBlank()) {
      throw new IllegalStateException(
          "bone.gateway.jwt.secret-key 不能为空，请通过 BONE_IAM_JWT_SECRET_KEY / BONE_JWT_SECRET 注入");
    }
    if (secretKey.length() < 32) {
      throw new IllegalStateException(
          "bone.gateway.jwt.secret-key 长度必须 >= 32 字节，当前长度: " + secretKey.length());
    }
    boolean isProd = environment != null && environment.acceptsProfiles(Profiles.of("prod"));
    boolean isDefault =
        DEFAULT_SECRET.equals(secretKey)
            || DEV_FALLBACK_SECRET.equals(secretKey)
            || secretKey.startsWith("change-me");
    if (isProd && isDefault) {
      throw new IllegalStateException(
          "生产环境禁止使用默认 JWT 密钥，请设置 BONE_IAM_JWT_SECRET_KEY / BONE_JWT_SECRET 环境变量");
    }
    if (isDefault) {
      log.warn("⚠️ 网关使用默认 JWT 密钥，仅限开发环境，生产环境将拒绝启动");
    }
    // RS256 公钥启动期 fail-fast：PEM 非法立即拒启
    if (rsaPublicKeyPem != null && !rsaPublicKeyPem.isBlank()) {
      com.bone.gateway.security.RsaKeyParser.publicKeyFromPem(rsaPublicKeyPem);
    }
  }

  /** Bearer 前缀。 */
  private String tokenPrefix = "Bearer ";

  /** Authorization 头名。 */
  private String headerName = "Authorization";

  /** RS256 公钥（PEM，X.509）。配置后，网关在 HS256 验签失败时会回退验 RS256（ADR-0005 双轨过渡）。 仅验证侧需要；与下游各模块配置同一公钥即可。 */
  private String rsaPublicKeyPem;

  /** JWKS kid，默认 bone-rsa-1；未来轮换时并行支持多 kid 重叠窗口。 */
  private String rsaKeyId = "bone-rsa-1";

  /** 免校验白名单路径前缀。 */
  private List<String> whitelist =
      new ArrayList<>(List.of("/api/v1/iam/auth/login", "/actuator/health", "/favicon.ico"));
}
