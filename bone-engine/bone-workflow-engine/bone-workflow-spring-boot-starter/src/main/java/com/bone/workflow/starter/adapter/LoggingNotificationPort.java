package com.bone.workflow.starter.adapter;

import com.bone.workflow.domain.WorkflowInstance;
import com.bone.workflow.domain.WorkflowTask;
import com.bone.workflow.ports.spi.NotificationPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 通知端口的<b>日志兜底实现</b>。
 *
 * <p>设计文档的口径是「实现可后置，先日志兜底并告警」：宿主接入站内信 / IM / 邮件前，用本实现保证「有通知意图」而不是 「没有通知能力」。宿主提供自己的 {@link
 * NotificationPort} bean 即覆盖本实现（自动配置用 {@code @ConditionalOnMissingBean}）。
 *
 * <p><b>挂起用 WARN 而非 INFO</b>：挂起意味着单据卡住、需要人工介入，必须能在日志里被 grep 到—— 混在 INFO 里等于没有告警。
 */
public class LoggingNotificationPort implements NotificationPort {

  private static final Logger log = LoggerFactory.getLogger(LoggingNotificationPort.class);

  @Override
  public void notifyTaskCreated(WorkflowTask task, WorkflowInstance instance) {
    log.info(
        "[workflow][notify] 待办通知：assignee={} instance={} node={} entity={}/{}",
        task.assigneeId(),
        task.instanceId(),
        task.nodeCode(),
        instance.entityCode(),
        instance.recordId());
  }

  @Override
  public void notifyUrged(
      WorkflowTask task, WorkflowInstance instance, String operatorId, String comment) {
    log.info(
        "[workflow][notify] 催办通知：assignee={} task={} operator={} comment={}",
        task.assigneeId(),
        task.id(),
        operatorId,
        comment);
  }

  @Override
  public void notifyReassigned(
      WorkflowTask task,
      WorkflowInstance instance,
      String newAssigneeId,
      String operatorId,
      String reason) {
    log.info(
        "[workflow][notify] 改派通知：{} → {} task={} operator={} reason={}",
        task.assigneeId(),
        newAssigneeId,
        task.id(),
        operatorId,
        reason);
  }

  @Override
  public void notifySuspended(WorkflowInstance instance, String reason) {
    log.warn(
        "[workflow][notify] 挂起告警（需人工介入）：instance={} entity={}/{} reason={}",
        instance.id(),
        instance.entityCode(),
        instance.recordId(),
        reason);
  }

  @Override
  public void notifyTerminated(WorkflowInstance instance, String operatorId, String reason) {
    log.info(
        "[workflow][notify] 终态通知：instance={} phase={} operator={} reason={}",
        instance.id(),
        instance.phase(),
        operatorId,
        reason);
  }

  @Override
  public void notifyTaskCancelled(WorkflowTask task, WorkflowInstance instance, String reason) {
    log.info(
        "[workflow][notify] 待办作废通知：assignee={} task={} instance={} reason={}",
        task.assigneeId(),
        task.id(),
        task.instanceId(),
        reason);
  }

  @Override
  public void notifyCc(
      WorkflowInstance instance, String nodeCode, java.util.List<String> recipients) {
    log.info(
        "[workflow][notify] 抄送知会：instance={} node={} recipients={}",
        instance.id(),
        nodeCode,
        recipients);
  }
}
