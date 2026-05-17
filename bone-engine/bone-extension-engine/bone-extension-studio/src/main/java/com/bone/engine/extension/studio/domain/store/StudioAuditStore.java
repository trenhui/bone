package com.bone.engine.extension.studio.domain.store;

import com.bone.core.model.PageResult;
import com.bone.engine.extension.studio.domain.model.StudioAuditEntry;

public interface StudioAuditStore {

    StudioAuditEntry save(StudioAuditEntry entry);

    PageResult<StudioAuditEntry> queryByCursor(String action, String resourceType, String cursor, int limit);
}
