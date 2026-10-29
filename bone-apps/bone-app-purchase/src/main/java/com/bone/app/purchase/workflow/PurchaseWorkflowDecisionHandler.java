package com.bone.app.purchase.workflow;

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
 * 采购「审批通过 / 驳回」逃逸处理器（bean 名 {@code purchaseWorkflowDecisionHandler}，approve 与 reject 两个动作共用）。
 *
 * <p>职责：把平台状态机动作翻译成引擎任务决策——按操作人匹配其<b>待办任务</b>（指派冻结在节点进入时已完成， 匹配不到即「不是当前 审批人」），再调 {@link
 * WorkflowEngine#completeTask}。节点完成判定（单签/会签原子推进）、余下待办作废、终态收敛全部由引擎负责。
 *
 * <p><b>补上的 SoD 缺口</b>：接入前，任何持 {@code runtime:purchase_order:approve} 码的人都能批任何单（平台动作级只验权限码、
 * 不验「是否是该单审批人」）；本 Handler 的任务匹配把决策权收敛到指派冻结时的审批人，绕过前端直调 API 同样 403。
 *
 * <p><b>幂等</b>：已决策任务的同结论重复提交由引擎幂等返回（网络重试不报错）；异结论改判被引擎拒绝（409）。
 *
 * <p>引擎引用经 {@link ObjectProvider} 惰性解析（理由见 {@link PurchaseWorkflowEngines}）。
 */
@Service("purchaseWorkflowDecisionHandler")
public class PurchaseWorkflowDecisionHandler implements RuntimeActionHandler {

  private final ObjectProvider<WorkflowEngine> workflowEngine;

  public PurchaseWorkflowDecisionHandler(ObjectProvider<WorkflowEngine> workflowEngine) {
    this.workflowEngine = workflowEngine;
  }

  @Override
  public Map<String, Object> execute(RuntimeActionContext ctx) {
    boolean approve = PurchaseWorkflowConstants.ACTION_APPROVE.equals(ctx.getDef().getActionCode());
    WorkflowInstance instance = requireActiveInstance(ctx);
    WorkflowTask task = requirePendingTaskOf(ctx, instance);
    String comment = text(ctx.getParams().get(approve ? "comment" : "reason"));
    // 驳回可选「退回指定节点」：透传给引擎，空则维持整单驳回原语义（引擎守卫 fail-closed）
    String backToNodeCode = text(ctx.getParams().get("backToNodeCode"));
    WorkflowInstance after;
    try {
      after =
          PurchaseWorkflowEngines.require(workflowEngine)
              .completeTask(
                  task.id(),
                  approve ? WorkflowTask.Decision.APPROVE : WorkflowTask.Decision.REJECT,
                  comment,
                  ctx.getOperatorId(),
                  backToNodeCode);
    } catch (WorkflowEngineException ex) {
      throw PurchaseWorkflowExceptionMapper.translate(ex);
    }
    Map<String, Object> result = new LinkedHashMap<>();
    result.put("taskId", task.id());
    result.put("decision", approve ? "APPROVE" : "REJECT");
    result.put("workflowPhase", after.phase().name());
    result.put("currentNode", after.currentNodeCode());
    if (!approve && backToNodeCode != null && !backToNodeCode.isBlank()) {
      result.put("backToNode", backToNodeCode);
    }
    // 单据 status 只能由**实例终态**驱动：一次决策 ≠ 流程结束。
    // 会签未齐 / 驳回退回指定节点时实例仍 RUNNING；此时照旧把 status 推到 to_status 会让单据
    // 离开 from_status（SUBMITTED），余下会签人的 approve 恒 409、待办永远清不掉（实测死单）。
    if (!after.isTerminated()) {
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
          PurchaseWorkflowEngines.require(workflowEngine)
              .findInstance(ctx.getEntityCode(), ctx.getRecordId());
    } catch (WorkflowEngineException ex) {
      throw PurchaseWorkflowExceptionMapper.translate(ex);
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
      pending = PurchaseWorkflowEngines.require(workflowEngine).listPendingTasks(instance.id());
    } catch (WorkflowEngineException ex) {
      throw PurchaseWorkflowExceptionMapper.translate(ex);
    }
    return pending.stream()
        .filter(t -> ctx.getOperatorId() != null && ctx.getOperatorId().equals(t.assigneeId()))
        .findFirst()
        .orElseThrow(
            () ->
                new RuntimeRecordException(
                    MetadataErrorCodes.RUNTIME_FORBIDDEN_ACTION,
                    "您不是该单据当前的审批人（审批权已按指派冻结，请联系当前办理人）"));
  }

  private static String text(Object value) {
    return value == null ? null : value.toString();
  }
}
