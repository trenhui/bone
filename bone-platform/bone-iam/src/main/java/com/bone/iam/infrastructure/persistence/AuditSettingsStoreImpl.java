package com.bone.iam.infrastructure.persistence;

import com.bone.core.util.DistributedIdGenerator;
import com.bone.iam.domain.audit.AuditSettings;
import com.bone.iam.domain.gateway.AuditSettingsStore;
import java.util.Map;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class AuditSettingsStoreImpl implements AuditSettingsStore {

    private final JdbcTemplate jdbcTemplate;

    @Override
    public AuditSettings findByTenantId(Long tenantId) {
        long effectiveTenant = tenantId != null ? tenantId : 0L;
        return jdbcTemplate.query(
                        """
                        SELECT retention_days, auto_archive_enabled, archive_after_days,
                               storage_type, worm_enabled
                        FROM iam_audit_settings
                        WHERE tenant_id = ?
                        """,
                        rs -> {
                            if (!rs.next()) {
                                return Optional.<AuditSettings>empty();
                            }
                            return Optional.of(mapRow(rs));
                        },
                        effectiveTenant)
                .orElseGet(this::defaults);
    }

    @Override
    public void upsert(Long tenantId, Map<String, Object> settings) {
        long effectiveTenant = tenantId != null ? tenantId : 0L;
        AuditSettings current = findByTenantId(effectiveTenant);
        int retention =
                settings.containsKey("retentionDays")
                        ? ((Number) settings.get("retentionDays")).intValue()
                        : current.getRetentionDays();
        boolean autoArchive =
                settings.containsKey("autoArchiveEnabled")
                        ? Boolean.TRUE.equals(settings.get("autoArchiveEnabled"))
                        : Boolean.TRUE.equals(current.isAutoArchiveEnabled());
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
                        : current.isWormEnabled();

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
                        effectiveTenant);
        if (updated == 0) {
            jdbcTemplate.update(
                    """
                    INSERT INTO iam_audit_settings
                    (id, tenant_id, retention_days, auto_archive_enabled, archive_after_days,
                     storage_type, worm_enabled, version)
                    VALUES (?, ?, ?, ?, ?, ?, ?, 0)
                    """,
                    DistributedIdGenerator.generateLongId(),
                    effectiveTenant,
                    retention,
                    autoArchive ? 1 : 0,
                    archiveAfter,
                    storageType,
                    worm ? 1 : 0);
        }
    }

    private static AuditSettings mapRow(java.sql.ResultSet rs) throws java.sql.SQLException {
        return AuditSettings.builder()
                .retentionDays(rs.getInt("retention_days"))
                .autoArchiveEnabled(rs.getBoolean("auto_archive_enabled"))
                .archiveAfterDays(rs.getInt("archive_after_days"))
                .storageType(rs.getString("storage_type"))
                .wormEnabled(rs.getBoolean("worm_enabled"))
                .build();
    }

    private AuditSettings defaults() {
        return AuditSettings.builder()
                .retentionDays(30)
                .autoArchiveEnabled(true)
                .archiveAfterDays(15)
                .storageType("DATABASE")
                .wormEnabled(false)
                .build();
    }
}
