package com.bone.app.expense.workflow;

import com.bone.metadata.runtime.application.RuntimeActionHandler;
import com.bone.metadata.runtime.domain.RuntimeActionContext;
import com.bone.workflow.domain.WorkflowEngine;
import com.bone.workflow.domain.WorkflowEngineException;
import com.bone.workflow.domain.WorkflowInstance;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

/**
 * 报销「提交」逃逸处理器（bean 名 {@code expenseWorkflowSubmitHandler}，与动作定义 {@code handler_bean} 对齐）。
 *
 * <p>职责：{@code submit}（DRAFT→SUBMITTED）的副作用是<b>启动审批流</b>——按流程定义 key 调 {@link WorkflowEngine#start}，
 * 由引擎完成「金额三档条件分支 → 指派解析（审批人进入节点时一次性冻结）→ 建待办任务」。 平台状态机的 {@code SUBMITTED} 流转仍由 {@code
 * RuntimeActionApplicationService} 在本 Handler 返回后统一负责，Handler 只做副作用。
 *
 * <p><b>重复提交幂等</b>：引擎对同一单据已有未终结实例时复用既有实例（网络重试 / 连点两次不会产生双份审批）。
 *
 * <p><b>挂起不是失败</b>：审批人解析不出（如大额单未填审批人、组织目录未接入）时引擎置 {@code SUSPENDED} 并返回—— Handler 在 结果里带 {@code
 * suspended=true} 提示前端「审批人待分配」，单据停在 SUBMITTED 等人工指派（{@code assignTask}），绝不静默放行。
 *
 * <p>引擎引用经 {@link ObjectProvider} 惰性解析（理由见 {@link ExpenseWorkflowEngines}）。
 */
@Service("expenseWorkflowSubmitHandler")
public class ExpenseWorkflowSubmitHandler implements RuntimeActionHandler {

  private final ObjectProvider<WorkflowEngine> workflowEngine;

  public ExpenseWorkflowSubmitHandler(ObjectProvider<WorkflowEngine> workflowEngine) {
    this.workflowEngine = workflowEngine;
  }

  @Override
  public Map<String, Object> execute(RuntimeActionContext ctx) {
    WorkflowInstance instance;
    try {
      instance =
          ExpenseWorkflowEngines.require(workflowEngine)
              .start(
                  ctx.getEntityCode(),
                  ctx.getRecordId(),
                  ExpenseWorkflowConstants.DEFINITION_KEY,
                  ctx.getOperatorId());
    } catch (WorkflowEngineException ex) {
      throw ExpenseWorkflowExceptionMapper.translate(ex);
    }
    Map<String, Object> result = new LinkedHashMap<>();
    result.put("workflowInstanceId", instance.id());
    result.put("workflowPhase", instance.phase().name());
    result.put("currentNode", instance.currentNodeCode());
    result.put("definitionVersion", instance.definitionVersion());
    if (instance.isSuspended()) {
      result.put("suspended", Boolean.TRUE);
      result.put("message", "审批人待分配（未能解析出审批人），单据已挂起，请联系管理员指派后继续审批");
    }
    return result;
  }
}
