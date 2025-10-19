package com.bone.metadata.sdk.support.audit;

import com.bone.metadata.sdk.domain.model.AuditLog;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class LoggingAuditService implements AuditService {
    private static final Logger log = LoggerFactory.getLogger(LoggingAuditService.class);

    /**
     * 默认把审计信息写到应用日志中。
     * 如果用户自己定义了 AuditService Bean，就不会使用这个。
     */
    @Override
    public void log(AuditLog auditLog) {
        // 直接访问字段而不是使用getter方法
        log.info("[Audit] traceId={} principal={} operation={} status={} timestamp={}{}",
                auditLog.getTraceId(),
                auditLog.getPrincipal(),
                auditLog.getOperation(),
                auditLog.getStatus(),
                auditLog.getTimestamp(),
                auditLog.getErrorMessage() != null
                        ? " error=" + auditLog.getErrorMessage()
                        : ""
        );
    }
}
