package com.bone.iam.application.query.handler;

import com.bone.core.tenant.context.TenantContext;
import com.bone.iam.application.query.dto.AuditSettingsDTO;
import com.bone.iam.domain.gateway.AuditSettingsStore;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class GetAuditSettingsQueryHandler {

  private final AuditSettingsStore auditSettingsStore;

  @Transactional(readOnly = true)
  public AuditSettingsDTO handle() {
    Long tenantId = TenantContext.getTenantIdAsLong();
    var settings = auditSettingsStore.findByTenantId(tenantId != null ? tenantId : 0L);
    AuditSettingsDTO dto = new AuditSettingsDTO();
    dto.setRetentionDays(settings.getRetentionDays());
    dto.setAutoArchiveEnabled(settings.isAutoArchiveEnabled());
    dto.setArchiveAfterDays(settings.getArchiveAfterDays());
    dto.setStorageType(settings.getStorageType());
    dto.setWormEnabled(settings.isWormEnabled());
    return dto;
  }
}
