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
            eq("task-1"), eq(WorkflowTask.Decision.APPROVE), eq("同意"), eq("u-manager"), any()))
        .thenReturn(instance("APPROVED"));

    Map<String, Object> result =
        handler.execute(ctx("approve", "u-manager", Map.of("comment", "同意")));

    verify(engine).completeTask("task-1", WorkflowTask.Decision.APPROVE, "同意", "u-manager", null);
    assertThat(result)
        .containsEntry("decision", "APPROVE")
        .containsEntry("workflowPhase", "APPROVED");
  }

  @Test
  @DisplayName("reject：completeTask(REJECT, reason)")
  void reject_completesTaskOfOperator() {
    stubRunningWithTask("u-manager");
    when(engine.completeTask(
            eq("task-1"), eq(WorkflowTask.Decision.REJECT), eq("缺发票"), eq("u-manager"), any()))
        .thenReturn(instance("REJECTED"));

    Map<String, Object> result =
        handler.execute(ctx("reject", "u-manager", Map.of("reason", "缺发票")));

    verify(engine).completeTask("task-1", WorkflowTask.Decision.REJECT, "缺发票", "u-manager", null);
    assertThat(result)
        .containsEntry("decision", "REJECT")
        .containsEntry("workflowPhase", "REJECTED");
  }

  /**
   * 退回指定节点 = 原地返工：实例退回上游节点后仍在 RUNNING，单据必须保持原状态。
   *
   * <p>不声明 KEEP 的后果是实测过的死单：平台把 status 推到 REJECTED（终态），而实例还在等审批， 审批人再点通过恒 409
   * ILLEGAL_TRANSITION，待办永远清不掉。
   */
  @Test
  @DisplayName("reject 带 backToNodeCode → 回显退回节点并要求平台不推进状态（KEEP）")
  void rejectToNode_declaresKeepStatus() {
    stubRunningWithTask("u-manager");
    when(engine.completeTask(
            eq("task-1"),
            eq(WorkflowTask.Decision.REJECT),
            eq("退回改金额"),
            eq("u-manager"),
            eq("start")))
        .thenReturn(instance("RUNNING"));

    Map<String, Object> result =
        handler.execute(
            ctx("reject", "u-manager", Map.of("reason", "退回改金额", "backToNodeCode", "start")));

    assertThat(result)
        .containsEntry("backToNode", "start")
        .containsEntry(
            RuntimeActionContext.OUTCOME_PLATFORM_STATUS, RuntimeActionContext.PLATFORM_STATUS_KEEP)
        .containsEntry("workflowPhase", "RUNNING");
  }

  @Test
  @DisplayName("整单驳回（无 backToNodeCode）不声明 KEEP：状态照常推进到 REJECTED")
  void plainReject_doesNotKeepStatus() {
    stubRunningWithTask("u-manager");
    when(engine.completeTask(any(), any(), any(), any(), any())).thenReturn(instance("REJECTED"));

    Map<String, Object> result =
        handler.execute(ctx("reject", "u-manager", Map.of("reason", "缺发票")));

    assertThat(result).doesNotContainKey(RuntimeActionContext.OUTCOME_PLATFORM_STATUS);
  }

  @Test
  @DisplayName("会签未齐时通过：实例仍 RUNNING ⇒ 声明 KEEP，否则余下会签人恒 409 死单")
  void approveWhileNodeStillPending_keepsStatus() {
    stubRunningWithTask("u-manager");
    // 会签节点：首位审批人通过后节点还差人，引擎不推进到下一节点，实例仍 RUNNING。
    when(engine.completeTask(
            eq("task-1"), eq(WorkflowTask.Decision.APPROVE), any(), eq("u-manager"), any()))
        .thenReturn(instance("RUNNING"));

    Map<String, Object> result =
        handler.execute(ctx("approve", "u-manager", Map.of("comment", "同意（会签之一）")));

    assertThat(result)
        .containsEntry(
            RuntimeActionContext.OUTCOME_PLATFORM_STATUS, RuntimeActionContext.PLATFORM_STATUS_KEEP)
        .containsEntry("workflowPhase", "RUNNING");
  }

  @Test
  @DisplayName("单签节点通过：实例 APPROVED 终态 ⇒ 不声明 KEEP，状态照常推进")
  void approveTerminal_doesNotKeepStatus() {
    stubRunningWithTask("u-manager");
    when(engine.completeTask(
            eq("task-1"), eq(WorkflowTask.Decision.APPROVE), any(), eq("u-manager"), any()))
        .thenReturn(instance("APPROVED"));

    Map<String, Object> result =
        handler.execute(ctx("approve", "u-manager", Map.of("comment", "同意")));

    assertThat(result).doesNotContainKey(RuntimeActionContext.OUTCOME_PLATFORM_STATUS);
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
    when(engine.completeTask(any(), any(), any(), any(), any()))
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
    when(engine.completeTask(any(), any(), any(), any(), any()))
        .thenThrow(new WorkflowEngineException(WorkflowErrorCode.ALREADY_DECIDED, "已决策"));

    assertThatThrownBy(() -> handler.execute(ctx("approve", "u-manager", Map.of())))
        .isInstanceOf(RuntimeRecordException.class)
        .satisfies(
            ex ->
                assertThat(((RuntimeRecordException) ex).getErrorCode())
                    .isEqualTo(MetadataErrorCodes.RUNTIME_ACTION_ILLEGAL_TRANSITION));
  }
}
