package com.bone.metadata.runtime.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.bone.core.model.PageResult;
import com.bone.metadata.catalog.domain.gateway.CurrentUserProvider;
import com.bone.metadata.engine.runtime.MetadataErrorCodes;
import com.bone.metadata.engine.runtime.RuntimeRecordException;
import com.bone.workflow.domain.WorkflowEngine;
import com.bone.workflow.domain.WorkflowInstance;
import com.bone.workflow.domain.WorkflowTask;
import com.bone.workflow.ports.spi.WorkflowInboxQuery;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.beans.factory.ObjectProvider;

/** {@link RuntimeApprovalApplicationService} 单元契约。 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class RuntimeApprovalApplicationServiceTest {

  private static final String OPERATOR = "1001";

  @Mock private WorkflowEngine engine;
  @Mock private RuntimeRecordApplicationService records;
  @Mock private RuntimeAccessGuard accessGuard;
  @Mock private CurrentUserProvider currentUser;
  @Mock private ObjectProvider<WorkflowEngine> engineProvider;
  @Mock private ObjectProvider<WorkflowInboxQuery> inboxProvider;

  private RuntimeApprovalApplicationService service;

  @BeforeEach
  void setUp() {
    when(currentUser.currentUserIdOrNull()).thenReturn(Long.valueOf(OPERATOR));
    when(engineProvider.getIfAvailable()).thenReturn(engine);
    service =
        new RuntimeApprovalApplicationService(
            engineProvider, inboxProvider, records, accessGuard, currentUser);
  }

  private WorkflowTask task(String id, String assignee, WorkflowTask.Status status) {
    return new WorkflowTask(
        id, "inst-1", "mgr", assignee, status, null, null, null, Instant.EPOCH, null);
  }

  private WorkflowInstance instance() {
    return new WorkflowInstance(
        "inst-1",
        "expense_reimbursement",
        "9001",
        "expense_reimbursement",
        1,
        "mgr",
        WorkflowInstance.Phase.RUNNING,
        "2002",
        Instant.EPOCH,
        Instant.EPOCH);
  }

  private WorkflowInstance suspendedInstance() {
    return new WorkflowInstance(
        "inst-1",
        "expense_reimbursement",
        "9001",
        "expense_reimbursement",
        1,
        "mgr",
        WorkflowInstance.Phase.SUSPENDED,
        "2002",
        Instant.EPOCH,
        Instant.EPOCH);
  }

  @Test
  void pendingTasks_returnEnginesTaskListWithRecordSummary() {
    when(engine.countPendingTasksByAssignee(OPERATOR)).thenReturn(1L);
    when(engine.listPendingTasksByAssignee(OPERATOR, 20, 0))
        .thenReturn(List.of(task("t-1", OPERATOR, WorkflowTask.Status.PENDING)));
    when(engine.findInstanceById("inst-1")).thenReturn(Optional.of(instance()));
    when(records.getById("expense_reimbursement", "9001"))
        .thenReturn(Map.of("claim_no", "BX-1", "amount", 1200, "title", "差旅"));

    PageResult<Map<String, Object>> page = service.listPendingTasks(1, 20);

    assertEquals(1, page.getTotal());
    Map<String, Object> row = page.getRecords().get(0);
    assertEquals("t-1", row.get("taskId"));
    assertEquals("expense_reimbursement", row.get("entityCode"));
    assertEquals("9001", row.get("recordId"));
    @SuppressWarnings("unchecked")
    Map<String, Object> summary = (Map<String, Object>) row.get("summary");
    assertEquals("BX-1", summary.get("claim_no"));
    assertEquals(1200, summary.get("amount"));
  }

  @Test
  void pendingTasks_summaryDegradesToNullWhenRowNotVisible() {
    when(engine.countPendingTasksByAssignee(OPERATOR)).thenReturn(1L);
    when(engine.listPendingTasksByAssignee(OPERATOR, 20, 0))
        .thenReturn(List.of(task("t-1", OPERATOR, WorkflowTask.Status.PENDING)));
    when(engine.findInstanceById("inst-1")).thenReturn(Optional.of(instance()));
    when(records.getById("expense_reimbursement", "9001"))
        .thenThrow(new RuntimeRecordException("META_404", "记录不存在"));

    Map<String, Object> row = service.listPendingTasks(1, 20).getRecords().get(0);

    assertNull(row.get("summary"), "摘要不可见不得拖垮待办主列表");
    assertEquals("t-1", row.get("taskId"));
  }

  @Test
  void pendingTasks_rejectsAnonymousOperator() {
    when(currentUser.currentUserIdOrNull()).thenReturn(null);

    RuntimeRecordException ex =
        assertThrows(RuntimeRecordException.class, () -> service.listPendingTasks(1, 20));

    assertEquals(MetadataErrorCodes.RUNTIME_FORBIDDEN_ACTION, ex.getErrorCode());
    verify(engine, never()).countPendingTasksByAssignee(anyString());
    verify(engine, never()).listPendingTasksByAssignee(anyString(), anyInt(), anyInt());
  }

  @Test
  void describe_returnsStartedFalseWhenNoInstance() {
    when(engine.findInstance("expense_reimbursement", "9001")).thenReturn(Optional.empty());

    Map<String, Object> result = service.describe("expense_reimbursement", "9001");

    assertEquals(Boolean.FALSE, result.get("started"));
    verify(accessGuard).requireRead("expense_reimbursement");
  }

  @Test
  void describe_exposesInstancePendingTasksAndTrail() {
    when(engine.findInstance("expense_reimbursement", "9001")).thenReturn(Optional.of(instance()));
    when(engine.listPendingTasks("inst-1"))
        .thenReturn(List.of(task("t-1", OPERATOR, WorkflowTask.Status.PENDING)));
    when(engine.listInstanceHistory("inst-1"))
        .thenReturn(List.of(task("t-0", OPERATOR, WorkflowTask.Status.APPROVED)));

    Map<String, Object> result = service.describe("expense_reimbursement", "9001");

    assertEquals(Boolean.TRUE, result.get("started"));
    assertEquals("RUNNING", ((Map<?, ?>) result.get("instance")).get("phase"));
    assertEquals(1, ((List<?>) result.get("pendingTasks")).size());
    assertEquals(1, ((List<?>) result.get("trail")).size());
  }

  /** 挂起原因必须随 describe 返回：挂起态没有待办，前端只看到「挂起」时无从判断该改哪个字段。 */
  @Test
  void describe_exposesSuspendReasonWhenInstanceSuspended() {
    com.bone.core.tenant.context.TenantContext.setTenantId(0L);
    try {
      when(engine.findInstance("expense_reimbursement", "9001"))
          .thenReturn(Optional.of(suspendedInstance()));
      when(engine.listPendingTasks("inst-1")).thenReturn(List.of());
      when(engine.listInstanceHistory("inst-1")).thenReturn(List.of());
      WorkflowInboxQuery inbox = org.mockito.Mockito.mock(WorkflowInboxQuery.class);
      when(inboxProvider.getIfUnique()).thenReturn(inbox);
      when(inbox.latestSuspensionNotice(0L, "inst-1"))
          .thenReturn(Optional.of("审批节点 mgr 解析出的审批人含发起人本人（自审批，违反职责分离）"));

      Map<String, Object> result = service.describe("expense_reimbursement", "9001");

      assertEquals("审批节点 mgr 解析出的审批人含发起人本人（自审批，违反职责分离）", result.get("suspendReason"));
      assertEquals("SUSPENDED", ((Map<?, ?>) result.get("instance")).get("phase"));
    } finally {
      com.bone.core.tenant.context.TenantContext.clear();
    }
  }

  /** 通知走 IM/邮件（不落本地表）时端口缺省返回空：describe 必须照常返回，只是没有原因字段。 */
  @Test
  void describe_omitsSuspendReasonWhenInboxCannotTell() {
    com.bone.core.tenant.context.TenantContext.setTenantId(0L);
    try {
      when(engine.findInstance("expense_reimbursement", "9001"))
          .thenReturn(Optional.of(suspendedInstance()));
      when(engine.listPendingTasks("inst-1")).thenReturn(List.of());
      when(engine.listInstanceHistory("inst-1")).thenReturn(List.of());
      when(inboxProvider.getIfUnique())
          .thenReturn(
              new WorkflowInboxQuery() {
                @Override
                public List<com.bone.workflow.ports.spi.WorkflowNotification> inbox(
                    long tenantId, String recipientId, boolean unreadOnly) {
                  return List.of();
                }
              });

      Map<String, Object> result = service.describe("expense_reimbursement", "9001");

      assertEquals(Boolean.TRUE, result.get("started"));
      assertNull(result.get("suspendReason"), "原因不可得时不得伪造，也不得让详情失败");
    } finally {
      com.bone.core.tenant.context.TenantContext.clear();
    }
  }

  @Test
  void engineAbsent_reportsDiagnosableFailure() {
    when(engineProvider.getIfAvailable()).thenReturn(null);

    RuntimeRecordException ex =
        assertThrows(
            RuntimeRecordException.class, () -> service.describe("expense_reimbursement", "1"));

    assertEquals(MetadataErrorCodes.RUNTIME_VALIDATION_FAILED, ex.getErrorCode());
    assertTrue(ex.getMessage().contains("审批流引擎未装配"));
  }

  @Test
  void terminate_requiresReason() {
    assertThrows(RuntimeRecordException.class, () -> service.terminate("inst-1", Map.of()));
    verify(engine, never()).terminateInstance(anyString(), any(), anyString());
  }

  @Test
  void reassign_forwardsToEngine() {
    when(engine.reassignTask("t-1", "3003", OPERATOR, "休假交接"))
        .thenReturn(task("t-1", "3003", WorkflowTask.Status.PENDING));

    Map<String, Object> result =
        service.reassign("t-1", Map.of("assigneeId", "3003", "reason", "休假交接"));

    assertEquals("t-1", result.get("taskId"));
    assertEquals("3003", result.get("assigneeId"));
    assertEquals("PENDING", result.get("status"));
    verify(engine).reassignTask(eq("t-1"), eq("3003"), eq(OPERATOR), eq("休假交接"));
  }

  @Test
  void assign_forwardsNodeNullableToEngine() {
    when(engine.assignTask("inst-1", null, "3003", OPERATOR, "接手挂起单")).thenReturn(instance());

    Map<String, Object> result =
        service.assign("inst-1", Map.of("assigneeId", "3003", "reason", "接手挂起单"));

    assertEquals("inst-1", result.get("instanceId"));
    assertEquals("RUNNING", result.get("phase"));
  }
}
