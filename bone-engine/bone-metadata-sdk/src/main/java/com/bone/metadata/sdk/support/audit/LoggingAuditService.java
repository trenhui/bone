package com.bone.metadata.sdk.support.audit;

import com.bone.metadata.sdk.domain.model.AuditLog;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class LoggingAuditService implements AuditService {

  /** 默认把审计信息写到应用日志中。 如果用户自己定义了 AuditService Bean，就不会使用这个。 */
  @Override
  public void log(AuditLog auditLog) {
    // 这里你可以把 auditLog 序列化为 JSON，或者格式化输出
    log.info(
        "[Audit] traceId={} principal={} operation={} status={} timestamp={}{}",
        auditLog.getTraceId(),
        auditLog.getPrincipal(),
        auditLog.getOperation(),
        auditLog.getStatus(),
        auditLog.getTimestamp(),
        auditLog.getErrorMessage() != null ? " error=" + auditLog.getErrorMessage() : "");
  }
}
