package com.bone.app.expense.workflow;

import com.bone.metadata.engine.runtime.MetadataErrorCodes;
import com.bone.metadata.engine.runtime.RuntimeRecordException;
import com.bone.metadata.runtime.application.RuntimeActionHandler;
import com.bone.metadata.runtime.domain.RuntimeActionContext;
import com.bone.workflow.domain.WorkflowEngine;
import com.bone.workflow.domain.WorkflowEngineException;
import com.bone.workflow.domain.WorkflowInstance;
import com.bone.workflow.domain.WorkflowTask;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

/**
 * 报销「审批通过 / 驳回」逃逸处理器（bean 名 {@code expenseWorkflowDecisionHandler}，approve 与 reject 两个动作共用）。
 *
 * <p>职责：把平台状态机动作翻译成引擎任务决策——按操作人匹配其<b>待办任务</b>（指派冻结在节点进入时已完成， 匹配不到即「不是当前 审批人」），再调 {@link
 * WorkflowEngine#completeTask}。节点完成判定（单签/会签原子推进）、余下待办作废、终态收敛全部由引擎负责。
 *
 * <p><b>补上的 SoD 缺口</b>：接入前，任何持 {@code runtime:expense_reimbursement:approve} 码的人都能批任何单（平台动作级只验权限码、
 * 不验「是否是该单审批人」）；本 Handler 的任务匹配把决策权收敛到指派冻结时的审批人，绕过前端直调 API 同样 403。
 *
 * <p><b>幂等</b>：已决策任务的同结论重复提交由引擎幂等返回（网络重试不报错）；异结论改判被引擎拒绝（409）。
 *
 * <p>引擎引用经 {@link ObjectProvider} 惰性解析（理由见 {@link ExpenseWorkflowEngines}）。
 */
@Service("expenseWorkflowDecisionHandler")
public class ExpenseWorkflowDecisionHandler implements RuntimeActionHandler {

  private final ObjectProvider<WorkflowEngine> workflowEngine;

  public ExpenseWorkflowDecisionHandler(ObjectProvider<WorkflowEngine> workflowEngine) {
    this.workflowEngine = workflowEngine;
  }

  @Override
  public Map<String, Object> execute(RuntimeActionContext ctx) {
    boolean approve = ExpenseWorkflowConstants.ACTION_APPROVE.equals(ctx.getDef().getActionCode());
    WorkflowInstance instance = requireActiveInstance(ctx);
    WorkflowTask task = requirePendingTaskOf(ctx, instance);
    String comment = text(ctx.getParams().get(approve ? "comment" : "reason"));
    // 驳回退回指定节点：审批人可选上游节点让单据原地返工（不整单驳回、不另起新单）。
    // APPROVE 时引擎忽略该参数；节点不合法时引擎抛 REJECT_TARGET_NODE_INVALID，绝不静默降级成整单驳回。
    String backToNodeCode = text(ctx.getParams().get("backToNodeCode"));
    WorkflowInstance after;
    try {
      after =
          ExpenseWorkflowEngines.require(workflowEngine)
              .completeTask(
                  task.id(),
                  approve ? WorkflowTask.Decision.APPROVE : WorkflowTask.Decision.REJECT,
                  comment,
                  ctx.getOperatorId(),
                  backToNodeCode);
    } catch (WorkflowEngineException ex) {
      throw ExpenseWorkflowExceptionMapper.translate(ex);
    }
    Map<String, Object> result = new LinkedHashMap<>();
    result.put("taskId", task.id());
    result.put("decision", approve ? "APPROVE" : "REJECT");
    result.put("workflowPhase", after.phase().name());
    result.put("currentNode", after.currentNodeCode());
    if (!approve && backToNodeCode != null && !backToNodeCode.isBlank()) {
      result.put("backToNode", backToNodeCode);
      // 退回节点 = 原地返工：实例已退回上游节点并重新派单，单据仍在审批中。
      // 若平台照常把 status 推到 REJECTED（终态），审批人再点「通过」恒 409、待办永远清不掉 —— 死单。
      result.put(
          RuntimeActionContext.OUTCOME_PLATFORM_STATUS, RuntimeActionContext.PLATFORM_STATUS_KEEP);
    }
    return result;
  }

  /** 取进行中的实例；不存在 / 已挂起 / 已终结都给可诊断的失败（fail-closed，不放行）。 */
  private WorkflowInstance requireActiveInstance(RuntimeActionContext ctx) {
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
    if (instance.isTerminated()) {
      throw new RuntimeRecordException(
          MetadataErrorCodes.RUNTIME_ACTION_ILLEGAL_TRANSITION,
          "审批流程已终结（" + instance.phase() + "），不能再审批");
    }
    if (instance.isSuspended()) {
      throw new RuntimeRecordException(
          MetadataErrorCodes.RUNTIME_VALIDATION_FAILED,
          "审批流程已挂起（审批人待分配），请联系管理员指派（assignTask）或恢复（resume）后继续");
    }
    return instance;
  }

  /** 操作人的待办任务；匹配不到 = 不是当前审批人（任务级 SoD，403）。 */
  private WorkflowTask requirePendingTaskOf(RuntimeActionContext ctx, WorkflowInstance instance) {
    List<WorkflowTask> pending;
    try {
      pending = ExpenseWorkflowEngines.require(workflowEngine).listPendingTasks(instance.id());
    } catch (WorkflowEngineException ex) {
      throw ExpenseWorkflowExceptionMapper.translate(ex);
    }
    return pending.stream()
        .filter(t -> ctx.getOperatorId() != null && ctx.getOperatorId().equals(t.assigneeId()))
        .findFirst()
        .orElseThrow(
            () ->
                new RuntimeRecordException(
                    MetadataErrorCodes.RUNTIME_FORBIDDEN_ACTION,
                    "您不是该单据当前的审批人（审批权已按金额分级指派并冻结，请联系当前办理人）"));
  }

  private static String text(Object value) {
    return value == null ? null : value.toString();
  }
}
