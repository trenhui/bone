package com.bone.engine.extension.studio.infrastructure.persistence.converter;

import com.bone.engine.extension.studio.domain.model.ExtPoint;
import com.bone.engine.extension.studio.domain.model.Extension;
import com.bone.core.domain.entity.AbstractEntity;
import com.bone.engine.extension.studio.infrastructure.persistence.entity.ExtStudioExtensionImpl;
import com.bone.engine.extension.studio.infrastructure.persistence.entity.ExtStudioExtensionPoint;
import com.bone.engine.extension.studio.sync.RuntimeExtensionSyncService;
import com.bone.engine.extension.support.sync.ExtensionRuntimeConfig;
import com.bone.engine.extension.support.sync.ExtensionRuntimeConfigParser;
import org.springframework.lang.NonNull;
import org.springframework.lang.Nullable;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Date;

/** 领域模型与 Metadata 持久化实体互转 */
public final class StudioPersistenceConverter {

    private StudioPersistenceConverter() {}

    @NonNull
    public static ExtStudioExtensionPoint toEntity(@NonNull ExtPoint domain) {
        ExtStudioExtensionPoint row = new ExtStudioExtensionPoint();
        row.setId(domain.getId());
        row.setPointName(domain.getName());
        row.setPointCode(resolvePointCode(domain));
        row.setDescription(domain.getDescription());
        row.setInterfaceName(domain.getInterfaceName());
        row.setBizDomain(domain.getDomain());
        row.setCategory(domain.getCategory());
        row.setStatus(domain.isEnabled() ? "ENABLED" : "DISABLED");
        applyAuditDefaults(row);
        return row;
    }

    @NonNull
    public static ExtPoint toDomain(@NonNull ExtStudioExtensionPoint row) {
        ExtPoint domain = new ExtPoint();
        domain.setId(row.getId());
        domain.setName(row.getPointName());
        domain.setDescription(row.getDescription());
        domain.setInterfaceName(row.getInterfaceName());
        domain.setDomain(row.getBizDomain());
        domain.setCategory(row.getCategory());
        domain.setEnabled("ENABLED".equalsIgnoreCase(row.getStatus()));
        return domain;
    }

    @NonNull
    public static ExtStudioExtensionImpl toEntity(@NonNull Extension domain) {
        ExtStudioExtensionImpl row = new ExtStudioExtensionImpl();
        row.setId(domain.getId());
        row.setExtensionPointId(domain.getExtPointId());
        row.setImplName(domain.getName());
        row.setImplCode(RuntimeExtensionSyncService.resolveExtensionCode(domain));
        row.setDescription(domain.getDescription());
        row.setClassName(domain.getClassName());
        row.setTenantCode(domain.getTenantCode());
        row.setBizCode(domain.getBizCode());
        row.setUseCase(domain.getUseCase());
        row.setScenario(domain.getScenario());
        row.setUserGroup(domain.getUserGroup());
        row.setPriority(domain.getPriority() != null ? domain.getPriority() : 100);
        row.setConfigJson(domain.getConfig());
        row.setStatus(domain.isEnabled() ? 1 : 0);

        ExtensionRuntimeConfig cfg = ExtensionRuntimeConfigParser.parse(domain.getConfig());
        row.setIsDefault(Boolean.TRUE.equals(cfg.getDefaultImpl()));
        row.setRolloutPercent(cfg.getTraffic());
        applyAuditDefaults(row);
        return row;
    }

    @NonNull
    public static Extension toDomain(@NonNull ExtStudioExtensionImpl row) {
        Extension domain = Extension.create(
                row.getExtensionPointId(),
                row.getImplName(),
                row.getDescription(),
                row.getClassName());
        domain.setId(row.getId());
        domain.setTenantCode(row.getTenantCode());
        domain.setBizCode(row.getBizCode());
        domain.setUseCase(row.getUseCase());
        domain.setScenario(row.getScenario());
        domain.setUserGroup(row.getUserGroup());
        domain.setPriority(row.getPriority());
        domain.setConfig(row.getConfigJson());
        domain.setEnabled(row.getStatus() != null && row.getStatus() == 1);
        domain.setCreateTime(toLocalDateTime(row.getCreateTime()));
        domain.setUpdateTime(toLocalDateTime(row.getUpdateTime()));
        return domain;
    }

    @NonNull
    public static String resolvePointCode(@NonNull ExtPoint domain) {
        if (StringUtils.hasText(domain.getInterfaceName())) {
            return domain.getInterfaceName().trim();
        }
        if (StringUtils.hasText(domain.getName())) {
            return domain.getName().trim();
        }
        return "EXT_POINT_" + (domain.getId() != null ? domain.getId() : "unknown");
    }

    @Nullable
    private static LocalDateTime toLocalDateTime(@Nullable Date date) {
        if (date == null) {
            return null;
        }
        return LocalDateTime.ofInstant(date.toInstant(), ZoneId.systemDefault());
    }

    @Nullable
    public static Date toDate(@Nullable LocalDateTime time) {
        if (time == null) {
            return null;
        }
        return Date.from(time.atZone(ZoneId.systemDefault()).toInstant());
    }

    private static void applyAuditDefaults(AbstractEntity<?> row) {
        Date now = new Date();
        if (row.getCreateTime() == null) {
            row.setCreateTime(now);
        }
        if (row.getUpdateTime() == null) {
            row.setUpdateTime(now);
        }
        if (row.getDeleted() == null) {
            row.setDeleted(false);
        }
    }
}
