package com.bone.iam.domain.model.audit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import com.bone.iam.domain.model.audit.event.AuditLogCreatedEvent;
import com.bone.iam.domain.model.audit.vo.OperationType;
import org.junit.jupiter.api.Test;

/** {@link AuditLog} 纯单测：审计日志快照创建与事件发布（无容器）。 */
class AuditLogTest {

  @Test
  void testCreateSnapshotsAuditEntry() {
    AuditLog auditLog =
        AuditLog.create(
            1L,
            99L,
            OperationType.LOGIN,
            "acc-1",
            "ACCOUNT",
            "10.0.0.1",
            "Mozilla/5.0",
            "{}",
            "SUCCESS",
            12);

    assertEquals(1L, auditLog.getTenantId());
    assertEquals(99L, auditLog.getUserId());
    assertEquals(OperationType.LOGIN, auditLog.getOperation());
    assertEquals("acc-1", auditLog.getResourceId());
    assertEquals("ACCOUNT", auditLog.getResourceType());
    assertEquals(12, auditLog.getDuration());
    assertNotNull(auditLog.getCreatedAt());
    assertEquals(1, auditLog.getDomainEvents().size());
    assertInstanceOf(AuditLogCreatedEvent.class, auditLog.getDomainEvents().get(0));
  }
}
