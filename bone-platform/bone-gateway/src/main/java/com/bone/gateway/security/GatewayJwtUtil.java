package com.bone.gateway.security;

import com.bone.gateway.config.GatewayJwtProperties;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.security.PublicKey;
import java.util.List;
import java.util.Optional;
import javax.crypto.SecretKey;
import lombok.Getter;
import org.springframework.stereotype.Component;

/** 网关侧 JWT 校验。与下游共享同一对称密钥与 claim 结构（userId/tenantId/scopes/subject）。 */
@Component
public class GatewayJwtUtil {

  private final GatewayJwtProperties properties;
  private final SecretKey signingKey;
  private final PublicKey rsaPublicKey;

  public GatewayJwtUtil(GatewayJwtProperties properties) {
    this.properties = properties;
    this.signingKey =
        Keys.hmacShaKeyFor(properties.getSecretKey().getBytes(StandardCharsets.UTF_8));
    this.rsaPublicKey =
        properties.getRsaPublicKeyPem() != null && !properties.getRsaPublicKeyPem().isBlank()
            ? RsaKeyParser.publicKeyFromPem(properties.getRsaPublicKeyPem())
            : null;
  }

  /** 从 Authorization 原始值解析并校验 token，成功返回 principal。双模：HS256 失败回退 RS256。 */
  public Optional<GatewayPrincipal> parse(String rawHeader) {
    if (rawHeader == null || rawHeader.isBlank()) {
      return Optional.empty();
    }
    String token = stripPrefix(rawHeader);
    if (token.isEmpty()) {
      return Optional.empty();
    }
    Optional<Claims> payload = tryParseHmac(token).or(() -> tryParseRsa(token));
    if (payload.isEmpty()) {
      return Optional.empty();
    }
    Claims claims = payload.get();
    String subject = claims.getSubject();
    if (subject == null || subject.isBlank()) {
      return Optional.empty();
    }
    GatewayPrincipal principal =
        new GatewayPrincipal(
            claims.get("userId", String.class),
            subject,
            claims.get("tenantId", String.class),
            readScopes(claims));
    return Optional.of(principal);
  }

  private Optional<Claims> tryParseHmac(String token) {
    try {
      return Optional.of(
          Jwts.parser().verifyWith(signingKey).build().parseSignedClaims(token).getPayload());
    } catch (Exception ignored) {
      return Optional.empty();
    }
  }

  private Optional<Claims> tryParseRsa(String token) {
    if (rsaPublicKey == null) {
      return Optional.empty();
    }
    try {
      return Optional.of(
          Jwts.parser().verifyWith(rsaPublicKey).build().parseSignedClaims(token).getPayload());
    } catch (Exception ignored) {
      return Optional.empty();
    }
  }

  @SuppressWarnings("unchecked")
  private static List<String> readScopes(Claims payload) {
    Object raw = payload.get("scopes");
    if (raw instanceof List<?> list) {
      return list.stream().map(String::valueOf).toList();
    }
    return java.util.Collections.emptyList();
  }

  private String stripPrefix(String token) {
    String prefix = properties.getTokenPrefix();
    if (prefix != null && !prefix.isBlank() && token.startsWith(prefix)) {
      return token.substring(prefix.length()).trim();
    }
    return token.trim();
  }

  /** 鉴权结果载体。 */
  @Getter
  public static class GatewayPrincipal {
    private final String userId;
    private final String username;
    private final String tenantId;
    private final List<String> scopes;

    public GatewayPrincipal(String userId, String username, String tenantId, List<String> scopes) {
      this.userId = userId;
      this.username = username;
      this.tenantId = tenantId;
      this.scopes = scopes;
    }
  }
}
