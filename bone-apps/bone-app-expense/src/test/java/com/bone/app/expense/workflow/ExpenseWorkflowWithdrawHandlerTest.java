package com.bone.app.expense.workflow;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.bone.metadata.engine.runtime.MetadataErrorCodes;
import com.bone.metadata.engine.runtime.RuntimeRecordException;
import com.bone.metadata.runtime.domain.RuntimeActionContext;
import com.bone.workflow.domain.WorkflowEngine;
import com.bone.workflow.domain.WorkflowEngineException;
import com.bone.workflow.domain.WorkflowErrorCode;
import com.bone.workflow.domain.WorkflowInstance;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** {@link ExpenseWorkflowWithdrawHandler}：withdraw 逃逸驱动引擎撤回（发起人校验 / 无实例 / 异常映射）。 */
class ExpenseWorkflowWithdrawHandlerTest {

  private final WorkflowEngine engine = mock(WorkflowEngine.class);
  private final ExpenseWorkflowWithdrawHandler handler =
      new ExpenseWorkflowWithdrawHandler(WorkflowFixtures.engineProvider(engine));

  private static RuntimeActionContext ctx(String operatorId) {
    return new RuntimeActionContext("expense_reimbursement", 7L, "9", null, Map.of(), operatorId);
  }

  private static WorkflowInstance instance(String phase, String startedBy) {
    return new WorkflowInstance(
        "wf-1",
        "expense_reimbursement",
        "9",
        "expense_reimbursement",
        1,
        "mgr",
        WorkflowInstance.Phase.valueOf(phase),
        startedBy,
        Instant.now(),
        Instant.now());
  }

  @Test
  @DisplayName("发起人撤回 → engine.withdraw，结果带 WITHDRAWN 终态")
  void initiatorWithdraw_succeeds() {
    when(engine.findInstance("expense_reimbursement", "9"))
        .thenReturn(Optional.of(instance("RUNNING", "u-applicant")));
    when(engine.withdraw("wf-1", "u-applicant")).thenReturn(instance("WITHDRAWN", "u-applicant"));

    Map<String, Object> result = handler.execute(ctx("u-applicant"));

    verify(engine).withdraw("wf-1", "u-applicant");
    assertThat(result).containsEntry("workflowPhase", "WITHDRAWN");
  }

  @Test
  @DisplayName("挂起实例允许发起人撤回（挂起不是终态，发起人不被死单锁死）")
  void suspendedInstance_withdrawAllowed() {
    when(engine.findInstance("expense_reimbursement", "9"))
        .thenReturn(Optional.of(instance("SUSPENDED", "u-applicant")));
    when(engine.withdraw("wf-1", "u-applicant")).thenReturn(instance("WITHDRAWN", "u-applicant"));

    Map<String, Object> result = handler.execute(ctx("u-applicant"));

    assertThat(result).containsEntry("workflowPhase", "WITHDRAWN");
  }

  @Test
  @DisplayName("非发起人撤回 → 403")
  void nonInitiator_forbidden() {
    when(engine.findInstance("expense_reimbursement", "9"))
        .thenReturn(Optional.of(instance("RUNNING", "u-applicant")));

    assertThatThrownBy(() -> handler.execute(ctx("u-other")))
        .isInstanceOf(RuntimeRecordException.class)
        .satisfies(
            ex ->
                assertThat(((RuntimeRecordException) ex).getErrorCode())
                    .isEqualTo(MetadataErrorCodes.RUNTIME_FORBIDDEN_ACTION))
        .hasMessageContaining("发起人");
  }

  @Test
  @DisplayName("无进行中实例 → 422")
  void noInstance_422() {
    when(engine.findInstance("expense_reimbursement", "9")).thenReturn(Optional.empty());

    assertThatThrownBy(() -> handler.execute(ctx("u-applicant")))
        .isInstanceOf(RuntimeRecordException.class)
        .satisfies(
            ex ->
                assertThat(((RuntimeRecordException) ex).getErrorCode())
                    .isEqualTo(MetadataErrorCodes.RUNTIME_VALIDATION_FAILED));
  }

  @Test
  @DisplayName("引擎 CONFLICT（并发撤回/驳回互斥）→ 409")
  void engineConflict_mapsTo409() {
    when(engine.findInstance("expense_reimbursement", "9"))
        .thenReturn(Optional.of(instance("RUNNING", "u-applicant")));
    when(engine.withdraw("wf-1", "u-applicant"))
        .thenThrow(new WorkflowEngineException(WorkflowErrorCode.CONFLICT, "流程状态已变更"));

    assertThatThrownBy(() -> handler.execute(ctx("u-applicant")))
        .isInstanceOf(RuntimeRecordException.class)
        .satisfies(
            ex ->
                assertThat(((RuntimeRecordException) ex).getErrorCode())
                    .isEqualTo(MetadataErrorCodes.RUNTIME_ACTION_ILLEGAL_TRANSITION));
  }
}
