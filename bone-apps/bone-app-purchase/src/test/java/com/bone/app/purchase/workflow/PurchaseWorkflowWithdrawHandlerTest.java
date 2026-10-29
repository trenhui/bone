package com.bone.app.purchase.workflow;

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
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;

/**
 * {@link PurchaseWorkflowWithdrawHandler}：withdraw 逃逸驱动引擎撤回。
 *
 * <p>四个用例对应四条真实路径：发起人撤回成功（含挂起态自助纠错）、非发起人 403、无实例 422、引擎并发冲突 409。 逐条为的是把「错误码翻译」钉死在测试里—— 平台前端按 code
 * 决定提示文案，映射一旦漂移，用户看到的就是「系统繁忙」。
 */
class PurchaseWorkflowWithdrawHandlerTest {

  private final WorkflowEngine engine = mock(WorkflowEngine.class);
  private final PurchaseWorkflowWithdrawHandler handler =
      new PurchaseWorkflowWithdrawHandler(provider(engine));

  private static ObjectProvider<WorkflowEngine> provider(WorkflowEngine engine) {
    DefaultListableBeanFactory bf = new DefaultListableBeanFactory();
    bf.registerSingleton("workflowEngine", engine);
    return bf.getBeanProvider(WorkflowEngine.class);
  }

  private static RuntimeActionContext ctx(String operatorId) {
    return new RuntimeActionContext("purchase_order", 1001L, "9", null, Map.of(), operatorId);
  }

  private static WorkflowInstance instance(String phase, String startedBy) {
    return new WorkflowInstance(
        "wf-1",
        "purchase_order",
        "9",
        PurchaseWorkflowConstants.DEFINITION_KEY,
        1,
        "dept",
        WorkflowInstance.Phase.valueOf(phase),
        startedBy,
        Instant.now(),
        Instant.now());
  }

  @Test
  @DisplayName("发起人撤回 → engine.withdraw，结果带 WITHDRAWN 终态")
  void initiatorWithdraw_succeeds() {
    when(engine.findInstance("purchase_order", "9"))
        .thenReturn(Optional.of(instance("RUNNING", "u-applicant")));
    when(engine.withdraw("wf-1", "u-applicant")).thenReturn(instance("WITHDRAWN", "u-applicant"));

    Map<String, Object> result = handler.execute(ctx("u-applicant"));

    verify(engine).withdraw("wf-1", "u-applicant");
    assertThat(result)
        .containsEntry("workflowPhase", "WITHDRAWN")
        .containsEntry("workflowInstanceId", "wf-1");
  }

  @Test
  @DisplayName("挂起实例允许发起人撤回（挂起不是终态，采购单不被死单锁死）")
  void suspendedInstance_withdrawAllowed() {
    when(engine.findInstance("purchase_order", "9"))
        .thenReturn(Optional.of(instance("SUSPENDED", "u-applicant")));
    when(engine.withdraw("wf-1", "u-applicant")).thenReturn(instance("WITHDRAWN", "u-applicant"));

    assertThat(handler.execute(ctx("u-applicant"))).containsEntry("workflowPhase", "WITHDRAWN");
  }

  @Test
  @DisplayName("非发起人撤回 → 403（采购员之间不能互相撤单）")
  void nonInitiator_forbidden() {
    when(engine.findInstance("purchase_order", "9"))
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
  @DisplayName("无进行中实例 → 422（未提交过 / 已归档）")
  void noInstance_422() {
    when(engine.findInstance("purchase_order", "9")).thenReturn(Optional.empty());

    assertThatThrownBy(() -> handler.execute(ctx("u-applicant")))
        .isInstanceOf(RuntimeRecordException.class)
        .satisfies(
            ex ->
                assertThat(((RuntimeRecordException) ex).getErrorCode())
                    .isEqualTo(MetadataErrorCodes.RUNTIME_VALIDATION_FAILED));
  }

  @Test
  @DisplayName("引擎 CONFLICT（审批人刚通关）→ 409 非法流转")
  void engineConflict_mapsTo409() {
    when(engine.findInstance("purchase_order", "9"))
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
