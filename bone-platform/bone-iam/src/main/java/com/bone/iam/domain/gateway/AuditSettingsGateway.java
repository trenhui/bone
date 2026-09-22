package com.bone.iam.domain.gateway;

import com.bone.iam.domain.model.audit.AuditSettings;
import java.util.Map;

/** 审计设置出站端口（读/写 {@code iam_audit_settings}）。 */
public interface AuditSettingsGateway {

  AuditSettings findByTenantId(Long tenantId);

  void upsert(Long tenantId, Map<String, Object> settings);
}
