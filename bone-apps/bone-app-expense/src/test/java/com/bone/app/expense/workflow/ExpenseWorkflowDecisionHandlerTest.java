package com.bone.app.expense.workflow;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.bone.metadata.engine.runtime.MetadataErrorCodes;
import com.bone.metadata.engine.runtime.RuntimeRecordException;
import com.bone.metadata.runtime.domain.RuntimeActionContext;
import com.bone.metadata.runtime.domain.RuntimeActionDef;
import com.bone.workflow.domain.WorkflowEngine;
import com.bone.workflow.domain.WorkflowEngineException;
import com.bone.workflow.domain.WorkflowErrorCode;
import com.bone.workflow.domain.WorkflowInstance;
import com.bone.workflow.domain.WorkflowTask;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * {@link ExpenseWorkflowDecisionHandler}：approve/reject 逃逸驱动引擎 completeTask（任务级 SoD / 挂起与终态守卫 /
 * 异常映射）。
 */
class ExpenseWorkflowDecisionHandlerTest {

  private final WorkflowEngine engine = mock(WorkflowEngine.class);
  private final ExpenseWorkflowDecisionHandler handler =
      new ExpenseWorkflowDecisionHandler(WorkflowFixtures.engineProvider(engine));

  private static RuntimeActionContext ctx(
      String actionCode, String operatorId, Map<String, Object> params) {
    RuntimeActionDef def =
        new RuntimeActionDef(
            1L,
            "expense_reimbursement",
            actionCode,
            actionCode,
            "SUBMITTED",
            "APPROVED".equals(actionCode) ? "APPROVED" : "REJECTED",
            "status",
            true,
            "expenseWorkflowDecisionHandler",
            List.of(),
            "runtime:expense_reimbursement:" + actionCode,
            false,
            20,
            false);
    return new RuntimeActionContext("expense_reimbursement", 7L, "9", def, params, operatorId);
  }

  private static WorkflowInstance instance(String phase) {
    return new WorkflowInstance(
        "wf-1",
        "expense_reimbursement",
        "9",
        "expense_reimbursement",
        1,
        "mgr",
        WorkflowInstance.Phase.valueOf(phase),
        "u-applicant",
        Instant.now(),
        Instant.now());
  }

  private static WorkflowTask pendingTask(String assignee) {
    return new WorkflowTask(
        "task-1",
        "wf-1",
        "mgr",
        assignee,
        WorkflowTask.Status.PENDING,
        null,
        null,
        null,
        Instant.now(),
        null);
  }

  private void stubRunningWithTask(String assignee) {
    when(engine.findInstance("expense_reimbursement", "9"))
        .thenReturn(Optional.of(instance("RUNNING")));
    when(engine.listPendingTasks("wf-1")).thenReturn(List.of(pendingTask(assignee)));
  }

  @Test
  @DisplayName("approve：匹配操作人待办 → completeTask(APPROVE, comment)")
  void approve_completesTaskOfOperator() {
    stubRunningWithTask("u-manager");
    when(engine.completeTask(
            eq("task-1"), eq(WorkflowTask.Decision.APPROVE), eq("同意"), eq("u-manager")))
        .thenReturn(instance("APPROVED"));

    Map<String, Object> result =
        handler.execute(ctx("approve", "u-manager", Map.of("comment", "同意")));

    verify(engine).completeTask("task-1", WorkflowTask.Decision.APPROVE, "同意", "u-manager");
    assertThat(result)
        .containsEntry("decision", "APPROVE")
        .containsEntry("workflowPhase", "APPROVED");
  }

  @Test
  @DisplayName("reject：completeTask(REJECT, reason)")
  void reject_completesTaskOfOperator() {
    stubRunningWithTask("u-manager");
    when(engine.completeTask(
            eq("task-1"), eq(WorkflowTask.Decision.REJECT), eq("缺发票"), eq("u-manager")))
        .thenReturn(instance("REJECTED"));

    Map<String, Object> result =
        handler.execute(ctx("reject", "u-manager", Map.of("reason", "缺发票")));

    verify(engine).completeTask("task-1", WorkflowTask.Decision.REJECT, "缺发票", "u-manager");
    assertThat(result)
        .containsEntry("decision", "REJECT")
        .containsEntry("workflowPhase", "REJECTED");
  }

  @Test
  @DisplayName("非当前审批人调 approve → 403（任务级 SoD：决策权收敛到指派冻结时的审批人）")
  void nonAssignee_forbidden() {
    stubRunningWithTask("u-manager");

    assertThatThrownBy(() -> handler.execute(ctx("approve", "u-other", Map.of())))
        .isInstanceOf(RuntimeRecordException.class)
        .satisfies(
            ex ->
                assertThat(((RuntimeRecordException) ex).getErrorCode())
                    .isEqualTo(MetadataErrorCodes.RUNTIME_FORBIDDEN_ACTION))
        .hasMessageContaining("不是该单据当前的审批人");
  }

  @Test
  @DisplayName("无进行中实例（未经工作流提交/已归档）→ 422")
  void noInstance_422() {
    when(engine.findInstance("expense_reimbursement", "9")).thenReturn(Optional.empty());

    assertThatThrownBy(() -> handler.execute(ctx("approve", "u-manager", Map.of())))
        .isInstanceOf(RuntimeRecordException.class)
        .satisfies(
            ex ->
                assertThat(((RuntimeRecordException) ex).getErrorCode())
                    .isEqualTo(MetadataErrorCodes.RUNTIME_VALIDATION_FAILED))
        .hasMessageContaining("没有进行中的审批流程");
  }

  @Test
  @DisplayName("实例已终结 → 409（迟到决策被拒绝，不改写终态）")
  void terminatedInstance_409() {
    when(engine.findInstance("expense_reimbursement", "9"))
        .thenReturn(Optional.of(instance("REJECTED")));

    assertThatThrownBy(() -> handler.execute(ctx("approve", "u-manager", Map.of())))
        .isInstanceOf(RuntimeRecordException.class)
        .satisfies(
            ex ->
                assertThat(((RuntimeRecordException) ex).getErrorCode())
                    .isEqualTo(MetadataErrorCodes.RUNTIME_ACTION_ILLEGAL_TRANSITION))
        .hasMessageContaining("已终结");
  }

  @Test
  @DisplayName("实例挂起（审批人待分配）→ 422，提示指派/恢复入口")
  void suspendedInstance_422() {
    when(engine.findInstance("expense_reimbursement", "9"))
        .thenReturn(Optional.of(instance("SUSPENDED")));

    assertThatThrownBy(() -> handler.execute(ctx("approve", "u-manager", Map.of())))
        .isInstanceOf(RuntimeRecordException.class)
        .hasMessageContaining("挂起");
  }

  @Test
  @DisplayName("引擎 NOT_ASSIGNEE（CAS 抢先/改判）→ 403")
  void engineNotAssignee_mapsTo403() {
    stubRunningWithTask("u-manager");
    when(engine.completeTask(any(), any(), any(), any()))
        .thenThrow(new WorkflowEngineException(WorkflowErrorCode.NOT_ASSIGNEE, "非办理人"));

    assertThatThrownBy(() -> handler.execute(ctx("approve", "u-manager", Map.of())))
        .isInstanceOf(RuntimeRecordException.class)
        .satisfies(
            ex ->
                assertThat(((RuntimeRecordException) ex).getErrorCode())
                    .isEqualTo(MetadataErrorCodes.RUNTIME_FORBIDDEN_ACTION));
  }

  @Test
  @DisplayName("引擎 ALREADY_DECIDED（异结论改判）→ 409")
  void engineAlreadyDecided_mapsTo409() {
    stubRunningWithTask("u-manager");
    when(engine.completeTask(any(), any(), any(), any()))
        .thenThrow(new WorkflowEngineException(WorkflowErrorCode.ALREADY_DECIDED, "已决策"));

    assertThatThrownBy(() -> handler.execute(ctx("approve", "u-manager", Map.of())))
        .isInstanceOf(RuntimeRecordException.class)
        .satisfies(
            ex ->
                assertThat(((RuntimeRecordException) ex).getErrorCode())
                    .isEqualTo(MetadataErrorCodes.RUNTIME_ACTION_ILLEGAL_TRANSITION));
  }
}
