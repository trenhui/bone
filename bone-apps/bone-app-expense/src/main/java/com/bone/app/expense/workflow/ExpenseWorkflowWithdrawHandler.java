package com.bone.app.expense.workflow;

import com.bone.metadata.engine.runtime.MetadataErrorCodes;
import com.bone.metadata.engine.runtime.RuntimeRecordException;
import com.bone.metadata.runtime.application.RuntimeActionHandler;
import com.bone.metadata.runtime.domain.RuntimeActionContext;
import com.bone.workflow.domain.WorkflowEngine;
import com.bone.workflow.domain.WorkflowEngineException;
import com.bone.workflow.domain.WorkflowInstance;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

/**
 * 报销「撤回」逃逸处理器（bean 名 {@code expenseWorkflowWithdrawHandler}，与动作定义 {@code handler_bean} 对齐）。
 *
 * <p>职责：发起人撤回（SUBMITTED→DRAFT）的副作用是终结审批流——{@link WorkflowEngine#withdraw} 作废全部待办任务、实例置 {@code
 * WITHDRAWN} 终态并释放活动实例槽位（允许修改后重新提交 = 新实例）。 平台状态机的 {@code DRAFT} 流转由平台在本 Handler 返回后统一负责。
 *
 * <p>发起人校验：仅流程发起人可撤回（引擎亦有同款守卫，此处提前给可诊断文案）；挂起实例同样允许撤回—— 挂起不是终态，发起人不能被 死单锁死（引擎硬纪律）。
 *
 * <p>引擎引用经 {@link ObjectProvider} 惰性解析（理由见 {@link ExpenseWorkflowEngines}）。
 */
@Service("expenseWorkflowWithdrawHandler")
public class ExpenseWorkflowWithdrawHandler implements RuntimeActionHandler {

  private final ObjectProvider<WorkflowEngine> workflowEngine;

  public ExpenseWorkflowWithdrawHandler(ObjectProvider<WorkflowEngine> workflowEngine) {
    this.workflowEngine = workflowEngine;
  }

  @Override
  public Map<String, Object> execute(RuntimeActionContext ctx) {
    Optional<WorkflowInstance> found;
    try {
      found =
          ExpenseWorkflowEngines.require(workflowEngine)
              .findInstance(ctx.getEntityCode(), ctx.getRecordId());
    } catch (WorkflowEngineException ex) {
      throw ExpenseWorkflowExceptionMapper.translate(ex);
    }
    if (found.isEmpty()) {
      throw new RuntimeRecordException(
          MetadataErrorCodes.RUNTIME_VALIDATION_FAILED, "该单据没有进行中的审批流程（未经工作流提交，或流程已归档）");
    }
    WorkflowInstance instance = found.get();
    if (ctx.getOperatorId() != null && !ctx.getOperatorId().equals(instance.startedBy())) {
      throw new RuntimeRecordException(
          MetadataErrorCodes.RUNTIME_FORBIDDEN_ACTION, "只有流程发起人才能撤回审批");
    }
    WorkflowInstance after;
    try {
      after =
          ExpenseWorkflowEngines.require(workflowEngine)
              .withdraw(instance.id(), ctx.getOperatorId());
    } catch (WorkflowEngineException ex) {
      throw ExpenseWorkflowExceptionMapper.translate(ex);
    }
    Map<String, Object> result = new LinkedHashMap<>();
    result.put("workflowInstanceId", after.id());
    result.put("workflowPhase", after.phase().name());
    return result;
  }
}
