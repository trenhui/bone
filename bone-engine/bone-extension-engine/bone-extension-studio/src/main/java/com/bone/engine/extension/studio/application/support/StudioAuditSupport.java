package com.bone.engine.extension.studio.application.support;

import com.bone.engine.extension.studio.config.StudioRequestContextFilter;
import com.bone.engine.extension.studio.domain.model.audit.StudioAuditEntry;
import com.bone.engine.extension.studio.domain.repository.StudioAuditRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.stereotype.Service;

/** 扩展 Studio 审计（[Audit] 日志 + 可选持久化）。 */
@Service
public class StudioAuditSupport {

  private static final Logger log = LoggerFactory.getLogger(StudioAuditSupport.class);

  private final StudioAuditRepository auditRepository;

  public StudioAuditSupport(StudioAuditRepository auditRepository) {
    this.auditRepository = auditRepository;
  }

  public void record(
      String action, String resourceType, String resourceId, String result, String detail) {
    String traceId = MDC.get(StudioRequestContextFilter.TRACE_ID);
    String userId = MDC.get("userId");
    String tenantId = MDC.get("tenantId");
    log.info(
        "[Audit] traceId={} tenantId={} userId={} action={} resourceType={} resourceId={} result={} detail={}",
        traceId,
        tenantId,
        userId,
        action,
        resourceType,
        resourceId,
        result,
        detail);
    StudioAuditEntry entry = new StudioAuditEntry();
    entry.setTraceId(traceId);
    entry.setTenantId(parseTenantId(tenantId));
    entry.setUserId(userId);
    entry.setAction(action);
    entry.setResourceType(resourceType);
    entry.setResourceId(resourceId);
    entry.setResult(result);
    entry.setDetail(detail);
    auditRepository.save(entry);
  }

  public void success(String action, String resourceType, String resourceId) {
    record(action, resourceType, resourceId, "SUCCESS", null);
  }

  public void failure(String action, String resourceType, String resourceId, String detail) {
    record(action, resourceType, resourceId, "FAILED", detail);
  }

  private static long parseTenantId(String tenantId) {
    if (tenantId == null || tenantId.isBlank()) {
      return 0L;
    }
    try {
      return Long.parseLong(tenantId);
    } catch (NumberFormatException ex) {
      return 0L;
    }
  }
}
