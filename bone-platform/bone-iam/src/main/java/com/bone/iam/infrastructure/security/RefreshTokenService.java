package com.bone.iam.infrastructure.security;

import com.bone.iam.domain.gateway.RefreshTokenIssuer;
import com.bone.iam.infrastructure.config.JwtConfig;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;

/**
 * 刷新令牌：明文仅返回客户端一次，库内仅存 SHA-256 哈希（对齐 {@code iam_refresh_token.token_hash}）。
 */
@Service
@RequiredArgsConstructor
public class RefreshTokenService implements RefreshTokenIssuer {

    private final NamedParameterJdbcTemplate jdbcTemplate;
    private final JwtConfig jwtConfig;

    @Override
    public String issue(Long accountId, Long tenantId) {
        String raw = UUID.randomUUID().toString().replace("-", "");
        String hash = sha256(raw);
        long id = System.currentTimeMillis();
        Instant expires = Instant.now().plusMillis(jwtConfig.getRefreshExpirationMs());
        jdbcTemplate.update(
                """
                INSERT INTO iam_refresh_token (id, tenant_id, account_id, token_hash, expires_at, is_revoked)
                VALUES (:id, :tenantId, :accountId, :hash, :expiresAt, 0)
                """,
                new MapSqlParameterSource()
                        .addValue("id", id)
                        .addValue("tenantId", tenantId != null ? tenantId : 0L)
                        .addValue("accountId", accountId)
                        .addValue("hash", hash)
                        .addValue("expiresAt", Timestamp.from(expires)));
        return raw;
    }

    @Override
    public Map<String, String> rotate(String rawRefreshToken) {
        String hash = sha256(rawRefreshToken);
        Map<String, Object> row = jdbcTemplate.query(
                """
                SELECT account_id, expires_at, is_revoked FROM iam_refresh_token
                WHERE token_hash = :hash LIMIT 1
                """,
                new MapSqlParameterSource("hash", hash),
                rs -> {
                    if (!rs.next()) {
                        return null;
                    }
                    return Map.of(
                            "accountId", rs.getLong("account_id"),
                            "expiresAt", rs.getTimestamp("expires_at"),
                            "revoked", rs.getInt("is_revoked"));
                });
        if (row == null) {
            throw new IllegalArgumentException("无效的刷新令牌");
        }
        if (((Number) row.get("revoked")).intValue() != 0) {
            throw new IllegalArgumentException("刷新令牌已撤销");
        }
        Timestamp expiresAt = (Timestamp) row.get("expiresAt");
        if (expiresAt.toInstant().isBefore(Instant.now())) {
            throw new IllegalArgumentException("刷新令牌已过期");
        }
        Long accountId = ((Number) row.get("accountId")).longValue();
        revokeByHash(hash);
        String newRaw = issue(accountId, 0L);
        return Map.of("accountId", String.valueOf(accountId), "refreshToken", newRaw);
    }

    @Override
    public void revoke(String rawRefreshToken) {
        if (rawRefreshToken == null || rawRefreshToken.isBlank()) {
            return;
        }
        revokeByHash(sha256(rawRefreshToken));
    }

    private void revokeByHash(String hash) {
        jdbcTemplate.update(
                "UPDATE iam_refresh_token SET is_revoked = 1 WHERE token_hash = :hash",
                new MapSqlParameterSource("hash", hash));
    }

    private static String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(bytes);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }
}
