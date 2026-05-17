package com.bone.engine.extension.studio.security;

import com.bone.engine.extension.studio.config.JwtConfig;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Optional;

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
            var payload = Jwts.parser()
                    .verifyWith(signingKey)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
            String userId = payload.get("userId", String.class);
            String username = payload.getSubject();
            if (username == null || username.isBlank()) {
                return Optional.empty();
            }
            return Optional.of(new JwtPrincipal(userId, username));
        } catch (Exception ignored) {
            return Optional.empty();
        }
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

    public record JwtPrincipal(String userId, String username) {}
}
