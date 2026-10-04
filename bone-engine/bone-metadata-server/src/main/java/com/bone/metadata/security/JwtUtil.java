package com.bone.metadata.security;

import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import jakarta.annotation.PostConstruct;
import java.util.Date;
import java.util.Map;
import java.util.UUID;
import javax.crypto.SecretKey;
import org.springframework.stereotype.Component;

/** JWT 工具类：生成与解析验证 */
@Component
public class JwtUtil {
  /** 生产环境请将密钥存储在安全配置中心，而非硬编码 */
  private SecretKey key;

  private final long expirationMillis = 1000L * 60 * 60; // 1 小时

  @PostConstruct
  public void init() {
    // jjwt 0.12.6：secretKeyFor(SignatureAlgorithm) 仍在（已弃用但可用），返回 SecretKey。
    key = Keys.secretKeyFor(SignatureAlgorithm.HS256);
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
