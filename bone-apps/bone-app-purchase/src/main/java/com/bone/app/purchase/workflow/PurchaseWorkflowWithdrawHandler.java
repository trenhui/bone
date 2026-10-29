package com.bone.app.purchase.workflow;

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
 * 采购「撤回」逃逸处理器（bean 名 {@code purchaseWorkflowWithdrawHandler}，与动作定义 {@code handler_bean} 对齐）。
 *
 * <p>职责：发起人撤回（SUBMITTED→DRAFT）的副作用是终结审批流——{@link WorkflowEngine#withdraw} 作废全部待办任务、实例置 {@code
 * WITHDRAWN} 终态并释放活动实例槽位（允许改后重提 = 新实例）。平台状态机的 {@code DRAFT} 流转由平台在本 Handler 返回后统一负责。
 *
 * <p><b>为什么采购必须有它</b>：接入前采购只有「提交 / 审批 / 驳回」三个逃逸动作，单据一旦提交就<b>只有审批人能推动</b>——下错金额、附件贴错、预算
 * 科目选错时，发起人除了求审批人驳回别无他法（驳回还会进 REJECTED 终态，要重新建单）。撤回让发起人在审批人动手前自助纠错，是企业采购最常用的一条路径。
 *
 * <p><b>发起人校验</b>：仅流程发起人可撤回（引擎亦有同款守卫，此处提前给可诊断文案，避免把引擎内部错误码透给业务用户）。 挂起实例同样允许撤回——挂起不是终态， 发起人不能被死单锁死。
 *
 * <p>引擎引用经 {@link ObjectProvider} 惰性解析（理由见 {@link PurchaseWorkflowEngines}）。
 */
@Service("purchaseWorkflowWithdrawHandler")
public class PurchaseWorkflowWithdrawHandler implements RuntimeActionHandler {

  private final ObjectProvider<WorkflowEngine> workflowEngine;

  public PurchaseWorkflowWithdrawHandler(ObjectProvider<WorkflowEngine> workflowEngine) {
    this.workflowEngine = workflowEngine;
  }

  @Override
  public Map<String, Object> execute(RuntimeActionContext ctx) {
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
    if (ctx.getOperatorId() != null && !ctx.getOperatorId().equals(instance.startedBy())) {
      throw new RuntimeRecordException(
          MetadataErrorCodes.RUNTIME_FORBIDDEN_ACTION, "只有流程发起人才能撤回审批");
    }
    WorkflowInstance after;
    try {
      after =
          PurchaseWorkflowEngines.require(workflowEngine)
              .withdraw(instance.id(), ctx.getOperatorId());
    } catch (WorkflowEngineException ex) {
      throw PurchaseWorkflowExceptionMapper.translate(ex);
    }
    Map<String, Object> result = new LinkedHashMap<>();
    result.put("workflowInstanceId", after.id());
    result.put("workflowPhase", after.phase().name());
    return result;
  }
}
