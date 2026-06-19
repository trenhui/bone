package com.bone.core.security.jwt;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Optional;
import javax.crypto.SecretKey;
import org.springframework.stereotype.Component;

/** 通用 JWT 签发与解析服务。各模块共用此实现。 */
@Component
public class JwtTokenService {

  private final JwtConfig jwtConfig;
  private final SecretKey signingKey;

  public JwtTokenService(JwtConfig jwtConfig) {
    this.jwtConfig = jwtConfig;
    this.signingKey = Keys.hmacShaKeyFor(jwtConfig.getSecretKey().getBytes(StandardCharsets.UTF_8));
  }

  /** 生成 JWT token（含 scopes）。 */
  public String generateToken(Long accountId, String username, Long tenantId, List<String> scopes) {
    List<String> safeScopes = scopes == null ? List.of() : scopes;
    return Jwts.builder()
        .subject(username)
        .claim("userId", String.valueOf(accountId))
        .claim("tenantId", String.valueOf(tenantId != null ? tenantId : 0L))
        .claim("scopes", safeScopes)
        .expiration(new Date(System.currentTimeMillis() + jwtConfig.getExpirationMs()))
        .signWith(signingKey)
        .compact();
  }

  /** 解析 JWT 返回 JwtPrincipal。 */
  public Optional<JwtPrincipal> parse(String rawToken) {
    return parseClaims(rawToken).map(this::toPrincipal);
  }

  /** 解析 JWT 返回 Claims。 */
  public Optional<Claims> parseClaims(String rawToken) {
    try {
      String token = stripPrefix(rawToken);
      Claims payload =
          Jwts.parser().verifyWith(signingKey).build().parseSignedClaims(token).getPayload();
      if (payload.getSubject() == null || payload.getSubject().isBlank()) {
        return Optional.empty();
      }
      return Optional.of(payload);
    } catch (Exception ignored) {
      return Optional.empty();
    }
  }

  private JwtPrincipal toPrincipal(Claims payload) {
    return new JwtPrincipal(
        payload.get("userId", String.class),
        payload.getSubject(),
        payload.get("tenantId", String.class),
        readScopes(payload));
  }

  @SuppressWarnings("unchecked")
  private static List<String> readScopes(Claims payload) {
    Object raw = payload.get("scopes");
    if (raw instanceof List<?> list) {
      return list.stream().map(String::valueOf).toList();
    }
    return Collections.emptyList();
  }

  /** 去除 "Bearer " 前缀。 */
  public String stripBearerToken(String token) {
    return stripPrefix(token);
  }

  private String stripPrefix(String token) {
    if (token == null) return "";
    String prefix = jwtConfig.getTokenPrefix();
    if (prefix != null && !prefix.isBlank() && token.startsWith(prefix)) {
      return token.substring(prefix.length()).trim();
    }
    return token.trim();
  }
}
