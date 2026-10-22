package com.bone.workflow.ports.spi;

import com.bone.workflow.domain.WorkflowInstance;
import com.bone.workflow.domain.WorkflowTask;

/**
 * 审批过程审计端口（端口）。
 *
 * <p>审批流是合规审计的重点对象（谁在什么时候批了什么、走了哪条分支）。宿主实现建议把每次决策写入平台既有的
 * 动作执行留痕链路，使审批轨迹与业务单据的操作日志在同一条时间线上——否则「审批记录」会成为与主数据割裂的第二本账。
 */
public interface WorkflowAuditPort {

  /** 记录流程启动。 */
  void recordStart(WorkflowInstance instance, String operatorId);

  /** 记录节点推进（走到哪个节点、命中哪条分支）。 */
  void recordAdvance(WorkflowInstance instance, String fromNode, String toNode, String branchLabel);

  /** 记录一次审批决策。 */
  void recordDecision(WorkflowTask task, WorkflowTask.Decision decision, String comment);

  /** 记录流程终态。 */
  void recordTermination(WorkflowInstance instance, String operatorId, String reason);

  /** 记录挂起（如审批人解析不出，等待人工介入）。 */
  void recordSuspension(WorkflowInstance instance, String reason);

  /** 记录恢复（挂起经修复后续跑）。 */
  void recordResumption(WorkflowInstance instance, String operatorId);
}
