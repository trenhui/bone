package com.bone.engine.extension.studio.infrastructure.persistence.converter;

import com.bone.core.domain.entity.AbstractEntity;
import com.bone.engine.extension.studio.domain.model.DeploymentStatus;
import com.bone.engine.extension.studio.domain.model.ExtPoint;
import com.bone.engine.extension.studio.domain.model.Extension;
import com.bone.engine.extension.studio.domain.model.PluginExecutionLog;
import com.bone.engine.extension.studio.domain.model.PluginVersion;
import com.bone.engine.extension.studio.domain.model.StudioAuditEntry;
import com.bone.engine.extension.studio.infrastructure.persistence.entity.ExtStudioAuditLog;
import com.bone.engine.extension.studio.infrastructure.persistence.entity.ExtStudioExtensionImpl;
import com.bone.engine.extension.studio.infrastructure.persistence.entity.ExtStudioExtensionPoint;
import com.bone.engine.extension.studio.infrastructure.persistence.entity.ExtStudioPluginExecutionLog;
import com.bone.engine.extension.studio.infrastructure.persistence.entity.ExtStudioPluginVersion;
import com.bone.engine.extension.studio.sync.RuntimeExtensionSyncService;
import com.bone.engine.extension.support.sync.ExtensionRuntimeConfig;
import com.bone.engine.extension.support.sync.ExtensionRuntimeConfigParser;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Date;
import org.springframework.lang.NonNull;
import org.springframework.lang.Nullable;
import org.springframework.util.StringUtils;

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
    row.setVersion(domain.getVersion());
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
    domain.setVersion(row.getVersion() != null ? row.getVersion() : 1);
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
    row.setVersion(domain.getVersion());
    applyAuditDefaults(row);
    return row;
  }

  @NonNull
  public static Extension toDomain(@NonNull ExtStudioExtensionImpl row) {
    Extension domain =
        Extension.create(
            row.getExtensionPointId(), row.getImplName(), row.getDescription(), row.getClassName());
    domain.setId(row.getId());
    domain.setTenantCode(row.getTenantCode());
    domain.setBizCode(row.getBizCode());
    domain.setUseCase(row.getUseCase());
    domain.setScenario(row.getScenario());
    domain.setUserGroup(row.getUserGroup());
    domain.setPriority(row.getPriority());
    domain.setConfig(row.getConfigJson());
    domain.setEnabled(row.getStatus() != null && row.getStatus() == 1);
    domain.setCreatedAt(toLocalDateTime(row.getCreatedAt()));
    domain.setUpdatedAt(toLocalDateTime(row.getUpdatedAt()));
    domain.setVersion(row.getVersion() != null ? row.getVersion() : 1);
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

  @NonNull
  public static ExtStudioPluginVersion toPluginVersionEntity(@NonNull PluginVersion domain) {
    ExtStudioPluginVersion row = new ExtStudioPluginVersion();
    row.setId(domain.getId());
    row.setPluginId(domain.getPluginId());
    row.setReleaseVersion(domain.getVersion());
    row.setFilePath(domain.getFilePath());
    row.setFileSize(domain.getFileSize());
    row.setChecksum(domain.getChecksum());
    row.setIsActive(domain.isActive());
    row.setDeploymentStatus(
        StringUtils.hasText(domain.getDeploymentStatus())
            ? domain.getDeploymentStatus()
            : DeploymentStatus.STAGED.name());
    row.setChangeLog(domain.getChangeLog());
    applyAuditDefaults(row);
    if (domain.getCreatedAt() != null) {
      row.setCreatedAt(toDate(domain.getCreatedAt()));
    }
    return row;
  }

  @NonNull
  public static PluginVersion toPluginVersionDomain(@NonNull ExtStudioPluginVersion row) {
    PluginVersion domain = new PluginVersion();
    domain.setId(row.getId());
    domain.setPluginId(row.getPluginId());
    domain.setVersion(row.getReleaseVersion());
    domain.setFilePath(row.getFilePath());
    domain.setFileSize(row.getFileSize() != null ? row.getFileSize() : 0L);
    domain.setChecksum(row.getChecksum());
    domain.setActive(Boolean.TRUE.equals(row.getIsActive()));
    domain.setDeploymentStatus(row.getDeploymentStatus());
    domain.setChangeLog(row.getChangeLog());
    domain.setCreatedAt(toLocalDateTime(row.getCreatedAt()));
    return domain;
  }

  @NonNull
  public static ExtStudioPluginExecutionLog toExecutionLogEntity(
      @NonNull PluginExecutionLog domain) {
    ExtStudioPluginExecutionLog row = new ExtStudioPluginExecutionLog();
    row.setId(domain.getId());
    row.setTenantId(domain.getTenantId());
    row.setPluginId(domain.getPluginId());
    row.setExtensionPointId(domain.getExtensionPointId());
    row.setExecutionId(domain.getExecutionId());
    row.setStatus(domain.getStatus());
    row.setInputData(domain.getInputData());
    row.setOutputData(domain.getOutputData());
    row.setErrorMessage(domain.getErrorMessage());
    row.setDurationMs(domain.getDurationMs());
    applyAuditDefaults(row);
    if (domain.getCreatedAt() != null) {
      row.setCreatedAt(toDate(domain.getCreatedAt()));
    }
    return row;
  }

  @NonNull
  public static PluginExecutionLog toExecutionLogDomain(@NonNull ExtStudioPluginExecutionLog row) {
    PluginExecutionLog domain = new PluginExecutionLog();
    domain.setId(row.getId());
    domain.setTenantId(row.getTenantId());
    domain.setPluginId(row.getPluginId());
    domain.setExtensionPointId(row.getExtensionPointId());
    domain.setExecutionId(row.getExecutionId());
    domain.setStatus(row.getStatus());
    domain.setInputData(row.getInputData());
    domain.setOutputData(row.getOutputData());
    domain.setErrorMessage(row.getErrorMessage());
    domain.setDurationMs(row.getDurationMs());
    domain.setCreatedAt(toLocalDateTime(row.getCreatedAt()));
    return domain;
  }

  @NonNull
  public static StudioAuditEntry toAuditDomain(@NonNull ExtStudioAuditLog row) {
    StudioAuditEntry entry = new StudioAuditEntry();
    entry.setId(row.getId());
    entry.setTenantId(row.getTenantId());
    entry.setTraceId(row.getTraceId());
    entry.setUserId(row.getUserId());
    entry.setAction(row.getAction());
    entry.setResourceType(row.getResourceType());
    entry.setResourceId(row.getResourceId());
    entry.setResult(row.getResult());
    entry.setDetail(row.getDetail());
    entry.setCreatedAt(toLocalDateTime(row.getCreatedAt()));
    return entry;
  }

  @NonNull
  public static ExtStudioAuditLog toAuditLogEntity(@NonNull StudioAuditEntry entry) {
    ExtStudioAuditLog row = new ExtStudioAuditLog();
    row.setTenantId(entry.getTenantId() != null ? entry.getTenantId() : 0L);
    row.setTraceId(entry.getTraceId());
    row.setUserId(entry.getUserId());
    row.setAction(entry.getAction());
    row.setResourceType(entry.getResourceType());
    row.setResourceId(entry.getResourceId());
    row.setResult(entry.getResult());
    row.setDetail(entry.getDetail());
    row.setCreatedAt(entry.getCreatedAt() != null ? toDate(entry.getCreatedAt()) : new Date());
    return row;
  }

  private static void applyAuditDefaults(AbstractEntity<?> row) {
    Date now = new Date();
    if (row.getCreatedAt() == null) {
      row.setCreatedAt(now);
    }
    if (row.getUpdatedAt() == null) {
      row.setUpdatedAt(now);
    }
    if (row.getDeleted() == null) {
      row.setDeleted(false);
    }
  }
}
