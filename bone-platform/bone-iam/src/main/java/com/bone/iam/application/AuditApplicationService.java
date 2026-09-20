package com.bone.iam.application;

import com.bone.core.model.PageResult;
import com.bone.iam.application.command.cmd.UpdateAuditSettingsCommand;
import com.bone.iam.application.query.dto.AuditLogDTO;
import com.bone.iam.application.query.dto.AuditSettingsDTO;
import com.bone.iam.application.query.qry.AuditLogListQuery;
import com.bone.iam.common.IamErrorCodes;
import com.bone.iam.common.IamErrors;
import com.bone.iam.domain.audit.AuditLog;
import com.bone.iam.domain.gateway.AuditSettingsGateway;
import com.bone.iam.domain.gateway.TenantProvider;
import com.bone.iam.domain.repository.AuditLogRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 审计应用层统一门面（Application Service First）——审计日志查询与审计设置管理的唯一入口。
 *
 * <p>原 {@code application/query/handler/AuditLogListQueryHandler}、{@code
 * GetAuditSettingsQueryHandler} 与 {@code
 * application/command/handler/UpdateAuditSettingsCommandHandler} 已全量内联进本类（E-3.11
 * 一次性大爆炸收敛）。适配器只依赖本类。
 *
 * <p>本类不出现读侧 DSL 与 {@code TenantContext}：日志检索下沉 {@link AuditLogRepository#findAuditLogPage}
 * （本聚合读，ADR-0030 / E-4.2），租户取值走 {@link TenantProvider} 端口（E-2）。
 */
@Service
@RequiredArgsConstructor
public class AuditApplicationService {

  private final AuditSettingsGateway auditSettingsGateway;
  private final AuditLogRepository auditLogRepository;
  private final TenantProvider tenantProvider;

  /** 分页查询审计日志（只读事务）。 */
  @Transactional(readOnly = true)
  public PageResult<AuditLogDTO> listLogs(AuditLogListQuery qry) {
    Long effectiveTenant = resolveTenantFilter(qry.getTenantId());
    PageResult<AuditLog> result =
        auditLogRepository.findAuditLogPage(
            qry.getUserId(),
            qry.getOperation(),
            qry.getResourceType(),
            qry.getResult(),
            qry.getStartedAt(),
            qry.getEndedAt(),
            effectiveTenant,
            qry.getPage(),
            qry.getSize());
    List<AuditLogDTO> dtoList =
        result.getRecords().stream().map(AuditApplicationService::toDto).toList();
    return PageResult.of(dtoList, result.getTotal(), result.getPage(), result.getSize());
  }

  /** 获取审计设置（只读事务）。 */
  @Transactional(readOnly = true)
  public AuditSettingsDTO getSettings() {
    Long tenantId = currentTenantId();
    var settings = auditSettingsGateway.findByTenantId(tenantId);
    AuditSettingsDTO dto = new AuditSettingsDTO();
    dto.setRetentionDays(settings.getRetentionDays());
    dto.setAutoArchiveEnabled(settings.isAutoArchiveEnabled());
    dto.setArchiveAfterDays(settings.getArchiveAfterDays());
    dto.setStorageType(settings.getStorageType());
    dto.setWormEnabled(settings.isWormEnabled());
    return dto;
  }

  /** 更新审计设置（写事务）。 */
  @Transactional
  public void updateSettings(UpdateAuditSettingsCommand cmd) {
    if (cmd == null || cmd.getSettings() == null || cmd.getSettings().isEmpty()) {
      throw IamErrors.of(IamErrorCodes.AUDIT_SETTINGS_REQUIRED, "审计设置不能为空");
    }
    auditSettingsGateway.upsert(currentTenantId(), cmd.getSettings());
  }

  /** 无租户上下文（内部入口）按平台租户 0 处理，与原 {@code TenantContext.getTenantIdAsLong()} 的兜底语义一致。 */
  private Long currentTenantId() {
    Long tenantId = tenantProvider.currentTenantIdOrNull();
    return tenantId != null ? tenantId : 0L;
  }

  /** 非平台租户（&gt; 0）强制按其过滤；平台租户（0）/无上下文回退到查询参数（详设 §3.4 / §4.8）。 */
  private Long resolveTenantFilter(Long fromQuery) {
    Long fromContext = tenantProvider.currentTenantIdOrNull();
    if (fromContext != null && fromContext != 0L) {
      return fromContext;
    }
    return fromQuery;
  }

  private static AuditLogDTO toDto(AuditLog auditLog) {
    AuditLogDTO dto = new AuditLogDTO();
    dto.setId(auditLog.getId());
    dto.setTenantId(auditLog.getTenantId());
    dto.setUserId(auditLog.getUserId());
    dto.setOperation(auditLog.getOperation());
    dto.setResourceId(auditLog.getResourceId());
    dto.setResourceType(auditLog.getResourceType());
    dto.setIp(auditLog.getIp());
    dto.setUserAgent(auditLog.getUserAgent());
    dto.setParameters(auditLog.getParameters());
    dto.setResult(auditLog.getResult());
    dto.setDuration(auditLog.getDuration());
    dto.setCreatedAt(auditLog.getCreatedAt());
    return dto;
  }
}
