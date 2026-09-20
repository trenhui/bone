package com.bone.iam.application.command.handler;

import com.bone.core.tenant.context.TenantContext;
import com.bone.iam.application.command.cmd.UpdateAuditSettingsCommand;
import com.bone.iam.domain.gateway.AuditSettingsGateway;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class UpdateAuditSettingsCommandHandler {

  private final AuditSettingsGateway auditSettingsStore;

  @Transactional
  public void handle(UpdateAuditSettingsCommand cmd) {
    if (cmd == null || cmd.getSettings() == null || cmd.getSettings().isEmpty()) {
      throw new IllegalArgumentException("审计设置不能为空");
    }
    Long tenantId = TenantContext.getTenantIdAsLong();
    auditSettingsStore.upsert(tenantId != null ? tenantId : 0L, cmd.getSettings());
  }
}
