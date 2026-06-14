package com.bone.iam.infrastructure.security;

import com.bone.iam.domain.gateway.AccessTokenIssuer;
import com.bone.iam.domain.permission.DefaultPermissionCodes;
import com.bone.iam.infrastructure.config.JwtConfig;
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

@Component
public class JwtTokenService implements AccessTokenIssuer {
  private final JwtConfig jwtConfig;
  private final SecretKey signingKey;

  public JwtTokenService(JwtConfig jwtConfig) {
    this.jwtConfig = jwtConfig;
    this.signingKey = Keys.hmacShaKeyFor(jwtConfig.getSecretKey().getBytes(StandardCharsets.UTF_8));
  }

  public String generateToken(Long accountId, String username) {
    return generateToken(accountId, username, 0L, DefaultPermissionCodes.adminFallback());
  }

  @Override
  public String issueAccessToken(
      Long accountId, String username, Long tenantId, List<String> scopes) {
    return generateToken(accountId, username, tenantId, scopes);
  }

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

  public Optional<JwtPrincipal> parse(String rawToken) {
    return parseClaims(rawToken).map(this::toPrincipal);
  }

  public Optional<Claims> parseClaims(String rawToken) {
    try {
      String token = stripPrefix(rawToken);
      Claims payload =
          Jwts.parser().verifyWith(signingKey).build().parseSignedClaims(token).getPayload();
      String username = payload.getSubject();
      if (username == null || username.isBlank()) {
        return Optional.empty();
      }
      return Optional.of(payload);
    } catch (Exception ignored) {
      return Optional.empty();
    }
  }

  private JwtPrincipal toPrincipal(Claims payload) {
    String userId = payload.get("userId", String.class);
    String username = payload.getSubject();
    String tenantId = payload.get("tenantId", String.class);
    return new JwtPrincipal(userId, username, tenantId, readScopes(payload));
  }

  @SuppressWarnings("unchecked")
  private static List<String> readScopes(Claims payload) {
    Object raw = payload.get("scopes");
    if (raw instanceof List<?> list) {
      return list.stream().map(String::valueOf).toList();
    }
    return Collections.emptyList();
  }

  public String stripBearerToken(String token) {
    return stripPrefix(token);
  }

  private String stripPrefix(String token) {
    if (token == null) {
      return "";
    }
    String prefix = jwtConfig.getTokenPrefix();
    if (prefix != null && !prefix.isBlank() && token.startsWith(prefix)) {
      return token.substring(prefix.length()).trim();
    }
    return token.trim();
  }

  public record JwtPrincipal(
      String userId, String username, String tenantId, List<String> scopes) {}
}
