package com.bone.engine.extension.studio.domain.gateway;

import com.bone.core.model.PageResult;
import com.bone.engine.extension.studio.domain.model.StudioAuditEntry;

/** Studio 审计读侧端口（ADR-0013）。 */
public interface StudioAuditReadPort {

    PageResult<StudioAuditEntry> queryByCursor(
            String action, String resourceType, String cursor, int limit);
}
