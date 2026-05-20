package com.bone.iam.infrastructure.persistence;

import com.bone.core.tenant.context.TenantContext;
import com.bone.core.util.DistributedIdGenerator;
import com.bone.iam.adapter.web.dto.resp.AuditSettingsResp;
import java.util.Map;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class AuditSettingsRepository {

    private final JdbcTemplate jdbcTemplate;

    public AuditSettingsResp findByTenant(Long tenantId) {
        return jdbcTemplate.query(
                        """
                        SELECT retention_days, auto_archive_enabled, archive_after_days,
                               storage_type, worm_enabled
                        FROM iam_audit_settings
                        WHERE tenant_id = ?
                        """,
                        rs -> {
                            if (!rs.next()) {
                                return Optional.<AuditSettingsResp>empty();
                            }
                            AuditSettingsResp resp = new AuditSettingsResp();
                            resp.setRetentionDays(rs.getInt("retention_days"));
                            resp.setAutoArchiveEnabled(rs.getBoolean("auto_archive_enabled"));
                            resp.setArchiveAfterDays(rs.getInt("archive_after_days"));
                            resp.setStorageType(rs.getString("storage_type"));
                            resp.setWormEnabled(rs.getBoolean("worm_enabled"));
                            return Optional.of(resp);
                        },
                        tenantId)
                .orElseGet(this::defaults);
    }

    public void upsert(Long tenantId, Map<String, Object> settings) {
        AuditSettingsResp current = findByTenant(tenantId);
        int retention =
                settings.containsKey("retentionDays")
                        ? ((Number) settings.get("retentionDays")).intValue()
                        : current.getRetentionDays();
        boolean autoArchive =
                settings.containsKey("autoArchiveEnabled")
                        ? Boolean.TRUE.equals(settings.get("autoArchiveEnabled"))
                        : Boolean.TRUE.equals(current.getAutoArchiveEnabled());
        int archiveAfter =
                settings.containsKey("archiveAfterDays")
                        ? ((Number) settings.get("archiveAfterDays")).intValue()
                        : current.getArchiveAfterDays();
        String storageType =
                settings.containsKey("storageType")
                        ? String.valueOf(settings.get("storageType"))
                        : current.getStorageType();
        boolean worm =
                settings.containsKey("wormEnabled")
                        ? Boolean.TRUE.equals(settings.get("wormEnabled"))
                        : Boolean.TRUE.equals(current.getWormEnabled());

        int updated =
                jdbcTemplate.update(
                        """
                        UPDATE iam_audit_settings
                        SET retention_days = ?, auto_archive_enabled = ?, archive_after_days = ?,
                            storage_type = ?, worm_enabled = ?, version = version + 1
                        WHERE tenant_id = ?
                        """,
                        retention,
                        autoArchive ? 1 : 0,
                        archiveAfter,
                        storageType,
                        worm ? 1 : 0,
                        tenantId);
        if (updated == 0) {
            jdbcTemplate.update(
                    """
                    INSERT INTO iam_audit_settings
                    (id, tenant_id, retention_days, auto_archive_enabled, archive_after_days,
                     storage_type, worm_enabled, version)
                    VALUES (?, ?, ?, ?, ?, ?, ?, 0)
                    """,
                    DistributedIdGenerator.generateLongId(),
                    tenantId,
                    retention,
                    autoArchive ? 1 : 0,
                    archiveAfter,
                    storageType,
                    worm ? 1 : 0);
        }
    }

    public Long resolveTenantId() {
        Long tenantId = TenantContext.getTenantId();
        return tenantId != null ? tenantId : 0L;
    }

    private AuditSettingsResp defaults() {
        AuditSettingsResp resp = new AuditSettingsResp();
        resp.setRetentionDays(30);
        resp.setAutoArchiveEnabled(true);
        resp.setArchiveAfterDays(15);
        resp.setStorageType("DATABASE");
        resp.setWormEnabled(false);
        return resp;
    }
}
