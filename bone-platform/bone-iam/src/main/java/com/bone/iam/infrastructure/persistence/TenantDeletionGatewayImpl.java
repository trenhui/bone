package com.bone.iam.infrastructure.persistence;

import com.bone.iam.domain.gateway.TenantDeletionGateway;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * 按租户清理 IAM 子表（无 DB 外键，顺序手动编排）。
 */
@Repository
@RequiredArgsConstructor
public class TenantDeletionGatewayImpl implements TenantDeletionGateway {

    private final NamedParameterJdbcTemplate jdbcTemplate;

    @Override
    public void purgeTenantData(long tenantId) {
        MapSqlParameterSource params = new MapSqlParameterSource("tenantId", tenantId);

        jdbcTemplate.update(
                """
                DELETE rp FROM iam_role_permission rp
                INNER JOIN iam_role r ON r.id = rp.role_id
                WHERE r.tenant_id = :tenantId
                """,
                params);

        jdbcTemplate.update("DELETE FROM iam_account_role WHERE tenant_id = :tenantId", params);
        jdbcTemplate.update("DELETE FROM iam_refresh_token WHERE tenant_id = :tenantId", params);
        jdbcTemplate.update("DELETE FROM iam_audit_log WHERE tenant_id = :tenantId", params);
        jdbcTemplate.update("DELETE FROM iam_audit_settings WHERE tenant_id = :tenantId", params);

        jdbcTemplate.update(
                "UPDATE iam_account SET deleted = 1, updated_at = NOW(3) WHERE tenant_id = :tenantId AND deleted = 0",
                params);
        jdbcTemplate.update(
                "UPDATE iam_role SET deleted = 1, updated_at = NOW(3) WHERE tenant_id = :tenantId AND deleted = 0",
                params);
    }
}
