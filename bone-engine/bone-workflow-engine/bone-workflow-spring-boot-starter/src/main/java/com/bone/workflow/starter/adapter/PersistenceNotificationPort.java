package com.bone.workflow.starter.adapter;

import com.bone.core.tenant.context.TenantContext;
import com.bone.workflow.domain.WorkflowInstance;
import com.bone.workflow.domain.WorkflowTask;
import com.bone.workflow.ports.spi.NotificationPort;
import com.bone.workflow.ports.spi.WorkflowIdGenerator;
import com.bone.workflow.starter.domain.WorkflowInboxQuery;
import com.bone.workflow.starter.domain.WorkflowNotification;
import com.bone.workflow.starter.sdk.WfNotificationRow;
import com.bone.workflow.starter.sdk.WfNotificationSdkRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 站内信通知端口的<b>持久化实现</b>（默认落地）。
 *
 * <p>把引擎在派单 / 催办 / 改派 / 挂起 / 终态时发出的通知写入 {@code wf_notification}，办理人据此查「我的待办/待阅」
 * 入口——审批流若只改状态不通知，会退化成「办事人每天自己刷待办列表」，催办、超时、改派全部失效。
 *
 * <p><b>租户来源</b>：{@code WorkflowTask}/{@code WorkflowInstance} 领域对象不携带 {@code tenant_id}（与 wf_*
 * 其余表一致， 租户走 {@link TenantContext} 线程上下文），故本实现从 {@link TenantContext#getTenantId()} 取租户；上下文缺省时回落
 * 0（与 wf_* 表 {@code DEFAULT 0} 口径一致）。
 *
 * <p><b>通知失败绝不阻断审批主链路</b>：引擎侧已包了一层 try-catch（{@code WorkflowEngineImpl#notify}），本实现
 * 再对单行写入做防御——SQLException 不应让一次「同意」变成「系统异常」。写入异常记 WARN 并吞掉（不向外抛）。
 *
 * <p>本实现同时实现 {@link WorkflowInboxQuery}，故默认既「发得出」也「查得到」；宿主若把通知接到 IM / 邮件网关， 可自备 {@code
 * NotificationPort} 覆盖写、但需另行提供 {@code WorkflowInboxQuery} 实现，否则站内信入口无数据。
 */
public class PersistenceNotificationPort implements NotificationPort, WorkflowInboxQuery {

  /** 挂起告警的接收人：平台管理员（运维介入）。宿主可按部署改默认，或经 {@link #setAdminRecipientId(String)} 调整。 */
  private static final String DEFAULT_ADMIN_RECIPIENT = "workflow-admin";

  private final WfNotificationSdkRepository repository;
  private final WorkflowIdGenerator idGenerator;
  private final Clock clock;

  private String adminRecipientId = DEFAULT_ADMIN_RECIPIENT;

  public PersistenceNotificationPort(
      WfNotificationSdkRepository repository, WorkflowIdGenerator idGenerator) {
    this(repository, idGenerator, Clock.systemUTC());
  }

  public PersistenceNotificationPort(
      WfNotificationSdkRepository repository, WorkflowIdGenerator idGenerator, Clock clock) {
    this.repository = repository;
    this.idGenerator = idGenerator;
    this.clock = clock == null ? Clock.systemUTC() : clock;
  }

  /** 调整挂起告警接收人（默认为 {@code workflow-admin}）。 */
  public void setAdminRecipientId(String adminRecipientId) {
    if (adminRecipientId != null && !adminRecipientId.isBlank()) {
      this.adminRecipientId = adminRecipientId;
    }
  }

  @Override
  public void notifyTaskCreated(WorkflowTask task, WorkflowInstance instance) {
    write(
        task.assigneeId(),
        "TASK_CREATED",
        instance,
        task.nodeCode(),
        "您有一条待审批",
        "单据 "
            + instance.entityCode()
            + "/"
            + instance.recordId()
            + " 等待您审批（节点 "
            + task.nodeCode()
            + "）",
        instance.startedBy());
  }

  @Override
  public void notifyUrged(
      WorkflowTask task, WorkflowInstance instance, String operatorId, String comment) {
    write(
        task.assigneeId(),
        "URGED",
        instance,
        task.nodeCode(),
        "催办提醒",
        "单据 " + instance.entityCode() + "/" + instance.recordId() + " 被催办：" + safe(comment),
        operatorId);
  }

  @Override
  public void notifyReassigned(
      WorkflowTask task,
      WorkflowInstance instance,
      String newAssigneeId,
      String operatorId,
      String reason) {
    // 通知「新办理人」有活接手（原办理人已脱手，无需再通知）。
    write(
        newAssigneeId,
        "REASSIGNED",
        instance,
        task.nodeCode(),
        "待办已转交给您",
        "单据 " + instance.entityCode() + "/" + instance.recordId() + " 转交原因：" + safe(reason),
        operatorId);
  }

  @Override
  public void notifySuspended(WorkflowInstance instance, String reason) {
    write(
        adminRecipientId,
        "SUSPENDED",
        instance,
        instance.currentNodeCode(),
        "审批流程挂起，需人工介入",
        "单据 " + instance.entityCode() + "/" + instance.recordId() + " 挂起：" + safe(reason),
        "workflow-engine");
  }

  @Override
  public void notifyTerminated(WorkflowInstance instance, String operatorId, String reason) {
    write(
        instance.startedBy(),
        "TERMINATED",
        instance,
        instance.currentNodeCode(),
        "审批流程已结束",
        "单据 " + instance.entityCode() + "/" + instance.recordId() + " 结束：" + safe(reason),
        operatorId == null ? "workflow-engine" : operatorId);
  }

  @Override
  public List<WorkflowNotification> inbox(long tenantId, String recipientId, boolean unreadOnly) {
    List<WfNotificationRow> rows =
        unreadOnly
            ? repository.selectInboxUnread(tenantId, recipientId)
            : repository.selectInboxAll(tenantId, recipientId);
    return rows.stream().map(PersistenceNotificationPort::toDomain).collect(Collectors.toList());
  }

  // ====================== 内部 ======================

  private void write(
      String recipientId,
      String type,
      WorkflowInstance instance,
      String nodeCode,
      String title,
      String body,
      String operatorId) {
    try {
      long tenantId = currentTenantId();
      Instant now = clock.instant();
      repository.insertNotification(
          idGenerator.nextId(),
          tenantId,
          recipientId,
          type,
          instance.id(),
          instance.entityCode(),
          instance.recordId(),
          nodeCode,
          title,
          body,
          now,
          operatorId == null ? "workflow-engine" : operatorId);
    } catch (RuntimeException ex) {
      // 通知失败绝不阻断审批主链路（双保险；引擎侧也已包 try-catch）。
      org.slf4j.LoggerFactory.getLogger(PersistenceNotificationPort.class)
          .warn("[workflow][notify] 站内信写入失败（不影响审批主链路）：type={} recipient={}", type, recipientId, ex);
    }
  }

  private static long currentTenantId() {
    try {
      Long tenant = TenantContext.getTenantIdAsLong();
      return tenant == null ? 0L : tenant;
    } catch (RuntimeException | Error ignored) {
      return 0L;
    }
  }

  private static WorkflowNotification toDomain(WfNotificationRow row) {
    return new WorkflowNotification(
        row.getId(),
        row.getTenantId() == null ? 0L : row.getTenantId(),
        row.getRecipientId(),
        row.getType(),
        row.getInstanceId(),
        row.getEntityCode(),
        row.getRecordId(),
        row.getNodeCode(),
        row.getTitle(),
        row.getBody(),
        row.getReadFlag() != 0,
        row.getCreatedAt());
  }

  private static String safe(String text) {
    return text == null ? "" : text;
  }
}
