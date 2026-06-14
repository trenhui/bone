package com.bone.system.infrastructure.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import javax.crypto.SecretKey;
import org.springframework.stereotype.Component;

/**
 * 只读 JWT 解析器：验签 + 提取 {@link JwtPrincipal}。
 *
 * <p>密钥来源 {@link SystemJwtProperties#getSecretKey()}，必须与 {@code bone-iam} 的 {@code
 * bone.iam.jwt.secret-key} 一致（HS256 对称）。生产环境通过环境变量 {@code BONE_IAM_JWT_SECRET_KEY} 注入。
 *
 * <p><b>不签发</b> token；签发统一由 {@code bone-iam} 负责，避免双密钥/旋转风险。
 */
@Component
public class JwtTokenParser {

  private final SystemJwtProperties properties;
  private final SecretKey signingKey;

  public JwtTokenParser(SystemJwtProperties properties) {
    this.properties = properties;
    this.signingKey =
        Keys.hmacShaKeyFor(properties.getSecretKey().getBytes(StandardCharsets.UTF_8));
  }

  /** 从 Bearer header 解析；任何异常一律返回 {@link Optional#empty()} 触发匿名访问。 */
  public Optional<JwtPrincipal> parse(String authHeader) {
    if (authHeader == null) {
      return Optional.empty();
    }
    String token = stripPrefix(authHeader);
    if (token.isEmpty()) {
      return Optional.empty();
    }
    try {
      Claims payload =
          Jwts.parser().verifyWith(signingKey).build().parseSignedClaims(token).getPayload();
      String username = payload.getSubject();
      if (username == null || username.isBlank()) {
        return Optional.empty();
      }
      return Optional.of(
          new JwtPrincipal(
              payload.get("userId", String.class),
              username,
              payload.get("tenantId", String.class),
              readScopes(payload)));
    } catch (Exception ignored) {
      return Optional.empty();
    }
  }

  private String stripPrefix(String token) {
    String prefix = properties.getTokenPrefix();
    if (prefix != null && !prefix.isBlank() && token.startsWith(prefix)) {
      return token.substring(prefix.length()).trim();
    }
    return token.trim();
  }

  private static List<String> readScopes(Claims payload) {
    Object raw = payload.get("scopes");
    if (raw instanceof List<?> list) {
      return list.stream().map(String::valueOf).toList();
    }
    return Collections.emptyList();
  }
}
