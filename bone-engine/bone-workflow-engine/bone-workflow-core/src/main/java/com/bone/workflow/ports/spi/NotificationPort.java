package com.bone.workflow.ports.spi;

import com.bone.workflow.domain.WorkflowInstance;
import com.bone.workflow.domain.WorkflowTask;

/**
 * 审批通知端口（v1 新增）。
 *
 * <p>审批流如果只改状态不通知，就会退化成「办事人每天自己去待办列表刷」——催办、超时、改派全都失效。故通知是审批流的 一等能力，而非锦上添花。
 *
 * <p><b>最关键的一条契约：通知失败绝不阻断审批主链路。</b>审批通过/驳回是主链路，必须成功；站内信/推送是尽力而为。
 * 若因为「短信网关挂了」就让审批失败，是把次要故障升级成主要故障。故实现方应自行捕获异常记 WARN，<b>不要向外抛</b>； 引擎侧也再包一层 try-catch
 * 兜底（双保险，因为宿主可能忘）。
 */
public interface NotificationPort {

  /** 任务创建：通知办理人「有你一个待办」。 */
  void notifyTaskCreated(WorkflowTask task, WorkflowInstance instance);

  /** 催办：提醒办理人尽快处理（无状态变更）。 */
  void notifyUrged(WorkflowTask task, WorkflowInstance instance, String operatorId, String comment);

  /** 改派：通知原办理人「已转出」与新办理人「有你一个待办」。 */
  void notifyReassigned(
      WorkflowTask task,
      WorkflowInstance instance,
      String newAssigneeId,
      String operatorId,
      String reason);

  /** 挂起告警：通知管理员「有单据卡住，需人工介入」。 */
  void notifySuspended(WorkflowInstance instance, String reason);

  /** 终态通知：告知发起人审批结果。 */
  void notifyTerminated(WorkflowInstance instance, String operatorId, String reason);

  /**
   * 抄送知会：把单据「知会」给抄送人（{@code CC} 节点）。
   *
   * <p><b>与 {@link #notifyTaskCreated} 的关键区别</b>：抄送<b>不产生待办、不要求决策、不参与节点完成判据</b>——
   * 它只是让相关人知情。若把抄送实现成「建一条待办」， 会签（ALL）节点会把抄送人算进「未决策」集合，流程将永远等不齐。
   *
   * @param instance 流程实例
   * @param nodeCode 抄送节点编码
   * @param recipients 抄送人账户 id（已去重）
   */
  void notifyCc(WorkflowInstance instance, String nodeCode, java.util.List<String> recipients);

  /** 空实现：宿主未接通知能力时使用（通知是尽力而为，缺失不应让引擎不可用）。 */
  NotificationPort NOOP =
      new NotificationPort() {
        @Override
        public void notifyTaskCreated(WorkflowTask task, WorkflowInstance instance) {}

        @Override
        public void notifyUrged(
            WorkflowTask task, WorkflowInstance instance, String operatorId, String comment) {}

        @Override
        public void notifyReassigned(
            WorkflowTask task,
            WorkflowInstance instance,
            String newAssigneeId,
            String operatorId,
            String reason) {}

        @Override
        public void notifySuspended(WorkflowInstance instance, String reason) {}

        @Override
        public void notifyTerminated(WorkflowInstance instance, String operatorId, String reason) {}

        @Override
        public void notifyCc(
            WorkflowInstance instance, String nodeCode, java.util.List<String> recipients) {}
      };
}
