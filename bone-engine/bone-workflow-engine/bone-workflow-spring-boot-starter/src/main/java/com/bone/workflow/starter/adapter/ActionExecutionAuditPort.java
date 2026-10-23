package com.bone.workflow.starter.adapter;

import com.bone.workflow.domain.WorkflowInstance;
import com.bone.workflow.domain.WorkflowTask;
import com.bone.workflow.ports.spi.WorkflowAuditPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 审批过程审计端口的平台实现。
 *
 * <p><b>设计取舍（当前为日志实现）</b>：审批流是合规审计的重点对象（谁在什么时候批了什么、走了哪条分支）。 平台既有的 {@code action_executions}
 * 链路是最理想的落点，但它由「业务单据动作」驱动，而审批决策的载体是 {@code wf_task}——两者尚未打通。打通前先用结构化日志承载，保证「有轨迹」而非「无迹可查」。
 *
 * <p><b>后续（L3）</b>：把审批决策写入 {@code wf_task} 的 decision/comment/decided_at 已持久化（见实例仓储），
 * 此处再补一条汇总审计即可与业务操作日志并列展示；不建议另建一本割裂的审批台账。
 */
public class ActionExecutionAuditPort implements WorkflowAuditPort {

  private static final Logger log = LoggerFactory.getLogger(ActionExecutionAuditPort.class);

  @Override
  public void recordStart(WorkflowInstance instance, String operatorId) {
    log.info(
        "[workflow] 流程启动：instance={} entity={} record={} def={}@v{} node={} operator={}",
        instance.id(),
        instance.entityCode(),
        instance.recordId(),
        instance.definitionKey(),
        instance.definitionVersion(),
        instance.currentNodeCode(),
        operatorId);
  }

  @Override
  public void recordAdvance(
      WorkflowInstance instance, String fromNode, String toNode, String branchLabel) {
    log.info(
        "[workflow] 节点推进：instance={} {} → {} (分支={})",
        instance.id(),
        fromNode,
        toNode,
        branchLabel);
  }

  @Override
  public void recordDecision(WorkflowTask task, WorkflowTask.Decision decision, String comment) {
    log.info(
        "[workflow] 审批决策：task={} instance={} node={} assignee={} decision={} comment={}",
        task.id(),
        task.instanceId(),
        task.nodeCode(),
        task.assigneeId(),
        decision,
        comment);
  }

  @Override
  public void recordTermination(WorkflowInstance instance, String operatorId, String reason) {
    log.info(
        "[workflow] 流程终结：instance={} phase={} operator={} reason={}",
        instance.id(),
        instance.phase(),
        operatorId,
        reason);
  }

  @Override
  public void recordSuspension(WorkflowInstance instance, String reason) {
    log.warn("[workflow] 流程挂起：instance={} reason={}", instance.id(), reason);
  }

  @Override
  public void recordResumption(WorkflowInstance instance, String operatorId) {
    log.info("[workflow] 流程恢复：instance={} operator={}", instance.id(), operatorId);
  }

  @Override
  public void recordReassignment(
      WorkflowTask task, String newAssigneeId, String operatorId, String reason) {
    log.info(
        "[workflow] 待办改派：task={} instance={} node={} 原办理人={} 新办理人={} operator={} reason={}",
        task.id(),
        task.instanceId(),
        task.nodeCode(),
        task.assigneeId(),
        newAssigneeId,
        operatorId,
        reason);
  }

  @Override
  public void recordUrge(WorkflowTask task, String operatorId, String comment) {
    log.info(
        "[workflow] 催办：task={} instance={} node={} assignee={} operator={} comment={}",
        task.id(),
        task.instanceId(),
        task.nodeCode(),
        task.assigneeId(),
        operatorId,
        comment);
  }
}
