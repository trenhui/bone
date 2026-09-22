package com.bone.iam.infrastructure.gateway;

import com.bone.iam.domain.gateway.RefreshTokenSessionGateway;
import com.bone.iam.domain.model.session.Session;
import java.sql.Timestamp;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

/** {@link RefreshTokenSessionGateway} 默认实现：直查 {@code iam_refresh_token}（脱敏，不返回 hash）。 */
@Repository
@RequiredArgsConstructor
public class RefreshTokenSessionGatewayAdapter implements RefreshTokenSessionGateway {

  private static final int MAX_LIST_SIZE = 200;

  private final NamedParameterJdbcTemplate jdbcTemplate;

  @Override
  public List<Session> listByAccountId(Long accountId) {
    if (accountId == null) {
      return List.of();
    }
    return jdbcTemplate.query(
        """
                SELECT id, account_id, tenant_id, is_revoked, expires_at, created_at
                FROM iam_refresh_token
                WHERE account_id = :accountId
                ORDER BY created_at DESC
                LIMIT :limit
                """,
        new MapSqlParameterSource()
            .addValue("accountId", accountId)
            .addValue("limit", MAX_LIST_SIZE),
        (rs, idx) -> mapRow(rs));
  }

  @Override
  public Optional<Session> findById(Long sessionId) {
    if (sessionId == null) {
      return Optional.empty();
    }
    List<Session> rows =
        jdbcTemplate.query(
            """
                SELECT id, account_id, tenant_id, is_revoked, expires_at, created_at
                FROM iam_refresh_token
                WHERE id = :id
                """,
            new MapSqlParameterSource("id", sessionId),
            (rs, idx) -> mapRow(rs));
    return rows.isEmpty() ? Optional.empty() : Optional.of(rows.get(0));
  }

  @Override
  public int revoke(Long sessionId) {
    if (sessionId == null) {
      return 0;
    }
    return jdbcTemplate.update(
        "UPDATE iam_refresh_token SET is_revoked = 1 WHERE id = :id AND is_revoked = 0",
        new MapSqlParameterSource("id", sessionId));
  }

  @Override
  public int revokeAllForAccount(Long accountId) {
    if (accountId == null) {
      return 0;
    }
    return jdbcTemplate.update(
        """
                UPDATE iam_refresh_token SET is_revoked = 1
                WHERE account_id = :accountId AND is_revoked = 0
                """,
        new MapSqlParameterSource("accountId", accountId));
  }

  private static Session mapRow(java.sql.ResultSet rs) throws java.sql.SQLException {
    Timestamp expires = rs.getTimestamp("expires_at");
    Timestamp created = rs.getTimestamp("created_at");
    return Session.builder()
        .id(rs.getLong("id"))
        .accountId(rs.getLong("account_id"))
        .tenantId(rs.getLong("tenant_id"))
        .revoked(rs.getInt("is_revoked") != 0)
        .expiresAt(expires == null ? null : expires.toLocalDateTime())
        .createdAt(created == null ? null : created.toLocalDateTime())
        .build();
  }
}
