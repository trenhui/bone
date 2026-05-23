package com.bone.engine.extension.studio.application.query.handler;

import com.bone.core.model.PageResult;
import com.bone.engine.extension.studio.domain.model.StudioAuditEntry;
import com.bone.engine.extension.studio.domain.gateway.StudioAuditReadPort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** 审计日志读侧。 */
@Component
@RequiredArgsConstructor
public class StudioAuditQueryHandler {

    private final StudioAuditReadPort studioAuditReadPort;

    public PageResult<StudioAuditEntry> queryByCursor(
            String action, String resourceType, String cursor, int limit) {
        int safeLimit = Math.min(100, Math.max(1, limit));
        return studioAuditReadPort.queryByCursor(action, resourceType, cursor, safeLimit);
    }
}
