package com.bone.engine.extension.studio.security;

import com.bone.engine.extension.studio.config.JwtConfig;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import javax.crypto.SecretKey;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class JwtTokenService {
  private final JwtConfig jwtConfig;
  private final SecretKey signingKey;

  public JwtTokenService(JwtConfig jwtConfig) {
    this.jwtConfig = jwtConfig;
    this.signingKey = Keys.hmacShaKeyFor(jwtConfig.getSecretKey().getBytes(StandardCharsets.UTF_8));
  }

  public Optional<JwtPrincipal> parse(String rawToken) {
    try {
      String token = stripPrefix(rawToken);
      var payload =
          Jwts.parser().verifyWith(signingKey).build().parseSignedClaims(token).getPayload();
      String userId = payload.get("userId", String.class);
      String username = payload.getSubject();
      if (username == null || username.isBlank()) {
        return Optional.empty();
      }
      String tenantId = null;
      Object tenantClaim = payload.get("tenantId");
      if (tenantClaim != null) {
        tenantId = tenantClaim.toString();
      }
      return Optional.of(
          new JwtPrincipal(
              userId,
              username,
              tenantId,
              parseScopes(payload.get("scopes"), payload.get("scope"))));
    } catch (Exception ignored) {
      return Optional.empty();
    }
  }

  @SuppressWarnings("unchecked")
  private static List<String> parseScopes(Object scopesClaim, Object scopeClaim) {
    List<String> scopes = new ArrayList<>();
    if (scopesClaim instanceof Collection<?> collection) {
      collection.stream().map(Object::toString).filter(StringUtils::hasText).forEach(scopes::add);
    } else if (scopesClaim instanceof String scopesString && StringUtils.hasText(scopesString)) {
      for (String part : scopesString.split("[,\\s]+")) {
        if (StringUtils.hasText(part)) {
          scopes.add(part.trim());
        }
      }
    }
    if (scopes.isEmpty()
        && scopeClaim instanceof String scopeString
        && StringUtils.hasText(scopeString)) {
      for (String part : scopeString.split("\\s+")) {
        if (StringUtils.hasText(part)) {
          scopes.add(part.trim());
        }
      }
    }
    return List.copyOf(scopes);
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
