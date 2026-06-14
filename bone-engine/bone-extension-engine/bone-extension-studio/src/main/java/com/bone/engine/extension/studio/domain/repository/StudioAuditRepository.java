package com.bone.engine.extension.studio.domain.repository;

import com.bone.engine.extension.studio.domain.model.StudioAuditEntry;

/** 审计写侧仓储；读见 {@link com.bone.engine.extension.studio.domain.gateway.StudioAuditReadPort}。 */
public interface StudioAuditRepository {

  StudioAuditEntry save(StudioAuditEntry entry);
}
