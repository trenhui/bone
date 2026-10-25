package com.bone.app.expense.workflow;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
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
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** {@link ExpenseWorkflowSubmitHandler}：submit 逃逸驱动引擎 start（定义 key / 幂等复用 / 挂起提示 / 异常映射）。 */
class ExpenseWorkflowSubmitHandlerTest {

  private final WorkflowEngine engine = mock(WorkflowEngine.class);
  private final ExpenseWorkflowSubmitHandler handler =
      new ExpenseWorkflowSubmitHandler(WorkflowFixtures.engineProvider(engine));

  private static RuntimeActionContext ctx(String recordId, String operatorId) {
    return new RuntimeActionContext(
        "expense_reimbursement", 7L, recordId, null, Map.of(), operatorId);
  }

  private static WorkflowInstance instance(String phase, String node) {
    return new WorkflowInstance(
        "wf-1",
        "expense_reimbursement",
        "9",
        "expense_reimbursement",
        1,
        node,
        WorkflowInstance.Phase.valueOf(phase),
        "u-applicant",
        Instant.now(),
        Instant.now());
  }

  @Test
  @DisplayName("start 按定义 key 启动，结果带实例/节点/版本（供前端与审计）")
  void happyPath_startsWithDefinitionKey() {
    when(engine.start("expense_reimbursement", "9", "expense_reimbursement", "u-applicant"))
        .thenReturn(instance("RUNNING", "mgr"));

    Map<String, Object> result = handler.execute(ctx("9", "u-applicant"));

    verify(engine).start("expense_reimbursement", "9", "expense_reimbursement", "u-applicant");
    assertThat(result)
        .containsEntry("workflowInstanceId", "wf-1")
        .containsEntry("workflowPhase", "RUNNING")
        .containsEntry("currentNode", "mgr")
        .containsEntry("definitionVersion", 1);
    assertThat(result).doesNotContainKey("suspended");
  }

  @Test
  @DisplayName("审批人解析不出 → 引擎挂起；结果带 suspended 提示，不静默放行")
  void suspended_flaggedInResult() {
    when(engine.start("expense_reimbursement", "9", "expense_reimbursement", "u-applicant"))
        .thenReturn(instance("SUSPENDED", "finance"));

    Map<String, Object> result = handler.execute(ctx("9", "u-applicant"));

    assertThat(result).containsEntry("suspended", Boolean.TRUE);
    assertThat(result.get("message").toString()).contains("挂起");
  }

  @Test
  @DisplayName("定义未发布（0056 未执行/租户无种子）→ 422 META_RUNTIME_VALIDATION_FAILED")
  void definitionNotFound_mapsTo422() {
    when(engine.start("expense_reimbursement", "9", "expense_reimbursement", "u-applicant"))
        .thenThrow(
            new WorkflowEngineException(WorkflowErrorCode.DEFINITION_NOT_FOUND, "未找到已发布的流程定义"));

    assertThatThrownBy(() -> handler.execute(ctx("9", "u-applicant")))
        .isInstanceOf(RuntimeRecordException.class)
        .satisfies(
            ex ->
                assertThat(((RuntimeRecordException) ex).getErrorCode())
                    .isEqualTo(MetadataErrorCodes.RUNTIME_VALIDATION_FAILED))
        .hasMessageContaining("未找到已发布的流程定义");
  }

  @Test
  @DisplayName("重复发起 → 引擎复用既有实例（幂等），结果即既有实例")
  void alreadyStarted_reusesInstance() {
    when(engine.start("expense_reimbursement", "9", "expense_reimbursement", "u-applicant"))
        .thenReturn(instance("RUNNING", "dept"));

    Map<String, Object> result = handler.execute(ctx("9", "u-applicant"));

    assertThat(result)
        .containsEntry("workflowInstanceId", "wf-1")
        .containsEntry("currentNode", "dept");
  }

  @Test
  @DisplayName("载荷不含 operatorId（dev 匿名主体）不影响调用形状")
  void nullOperator_stillCallsEngine() {
    when(engine.start(eq("expense_reimbursement"), eq("9"), eq("expense_reimbursement"), eq(null)))
        .thenReturn(instance("RUNNING", "mgr"));

    Map<String, Object> result = handler.execute(ctx("9", null));

    assertThat(result).containsEntry("workflowPhase", "RUNNING");
  }

  @Test
  @DisplayName("引擎未装配 → IllegalStateException（500 语义：平台配置问题，执行时明确失败而非打挂上下文）")
  void engineAbsent_failsAtExecution() {
    ExpenseWorkflowSubmitHandler detached =
        new ExpenseWorkflowSubmitHandler(WorkflowFixtures.emptyProvider(WorkflowEngine.class));

    assertThatThrownBy(() -> detached.execute(ctx("9", "u1")))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("引擎未装配");
  }

  @Test
  @DisplayName("空参数 Map 不触发 NPE")
  void emptyParams_noNpe() {
    when(engine.start("expense_reimbursement", "9", "expense_reimbursement", "u-applicant"))
        .thenReturn(instance("RUNNING", "mgr"));

    Map<String, Object> params = new LinkedHashMap<>();
    Map<String, Object> result =
        handler.execute(
            new RuntimeActionContext(
                "expense_reimbursement", 7L, "9", null, params, "u-applicant"));

    assertThat(result).containsEntry("currentNode", "mgr");
  }
}
