package com.bone.metadata.security;

import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import jakarta.annotation.PostConstruct;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.Map;
import java.util.UUID;
import javax.crypto.SecretKey;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** JWT 工具类：生成与解析验证 */
@Component
public class JwtUtil {

  private static final Logger log = LoggerFactory.getLogger(JwtUtil.class);

  /**
   * 与 IAM 共享的签名密钥：资源服务端须用它校验 IAM 签发的令牌。
   *
   * <p>优先读 IAM 专用密钥（{@code bone.iam.jwt.secret.key} ← env {@code BONE_IAM_JWT_SECRET_KEY}）， 其次通用
   * JWT 密钥（{@code bone.jwt.secret} ← env {@code BONE_JWT_SECRET}）。两者皆空时回落为每次启动随机密钥（仅 dev /
   * 单测：本实例自签自验，无法校验 IAM 令牌）。
   */
  @Value("${bone.iam.jwt.secret.key:}")
  private String iamJwtSecretKey;

  @Value("${bone.jwt.secret:}")
  private String jwtSecret;

  private SecretKey key;

  private final long expirationMillis = 1000L * 60 * 60; // 1 小时

  @PostConstruct
  public void init() {
    // 生产 / 联调：使用与 IAM 一致的共享密钥校验令牌（HS256 需 ≥256bit / 32 字节）。
    String secret = firstNonBlank(iamJwtSecretKey, jwtSecret);
    if (secret != null && secret.getBytes(StandardCharsets.UTF_8).length >= 32) {
      this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
      return;
    }
    // 兜底（仅 dev / 单测）：每次启动生成随机密钥——本实例自签自验，无法校验 IAM 令牌。
    // 显式告警：生产/联调若走到这里，意味着 bone.iam.jwt.secret.key / bone.jwt.secret 未配置或密钥 <32 字节，
    // 所有 IAM 签发的令牌都会被拒（请求匿名化），必须当场暴露而非静默降级。
    this.key = Keys.secretKeyFor(SignatureAlgorithm.HS256);
    log.warn(
        "JwtUtil 回退为随机密钥：本实例自签自验，无法校验 IAM 签发的令牌（仅限 dev/单测）。"
            + "生产/联调须配置 bone.iam.jwt.secret.key（env BONE_IAM_JWT_SECRET_KEY）且与 IAM 一致（≥32 字节）；"
            + "否则所有鉴权请求将被匿名化。");
  }

  /**
   * 返回首个非空候选。
   *
   * <p>单测直接 {@code new JwtUtil()} 手动触发 {@link #init()}（不过 Spring），{@code @Value} 字段为 null——
   * 此处统一兜底，避免 NPE（2026-10-25 回归：{@code JwtAuthenticationFilterTest} 10 例全因此挂）。
   */
  private static String firstNonBlank(String... candidates) {
    for (String candidate : candidates) {
      // 用 isBlank 而非 isEmpty：密钥若被配成空白串（如 " "），isEmpty 会判定为「已配置」，
      // 随后 Keys.hmacShaKeyFor(1 字节) 抛 WeakKeyException 直接把启动打挂。空白串应视为未配置并正常回退告警。
      if (candidate != null && !candidate.isBlank()) {
        return candidate;
      }
    }
    return null;
  }

  /**
   * 生成 JWT
   *
   * @param subject 用户标识（用户名或用户ID）
   * @param claims 自定义载荷（如 roles）
   */
  public String generateToken(String subject, Map<String, Object> claims) {
    long now = System.currentTimeMillis();
    return Jwts.builder()
        .setId(UUID.randomUUID().toString())
        .setClaims(claims)
        .setSubject(subject)
        .setIssuedAt(new Date(now))
        .setExpiration(new Date(now + expirationMillis))
        .signWith(key)
        .compact();
  }

  /**
   * 解析并验证 JWT，若无效或过期则抛出异常
   *
   * <p><b>为什么用 0.12.x 写法</b>：本类此前用 {@code Jwts.parserBuilder().setSigningKey(key).build()
   * .parseClaimsJws(token).getBody()}，那是 jjwt 0.11 的 API——0.12 起{@code parserBuilder()} 与 {@code
   * setSigningKey} 已移除（改名为 {@code parser()} + {@code verifyWith}，{@code parseClaimsJws} 改名{@code
   * parseSignedClaims}）。Spring Boot BOM 托管的 jjwt 升到 0.12.6 后本模块编译失败 （{@code 找不到符号: 方法
   * parserBuilder()}，2026-10-03）。
   *
   * <p>迁移范式与 {@code bone-security/JwtTokenService}、{@code bone-gateway/GatewayJwtUtil} 保持一致。
   */
  public Claims parseToken(String token) throws JwtException {
    return Jwts.parser().verifyWith(key).build().parseSignedClaims(token).getPayload();
  }
}
