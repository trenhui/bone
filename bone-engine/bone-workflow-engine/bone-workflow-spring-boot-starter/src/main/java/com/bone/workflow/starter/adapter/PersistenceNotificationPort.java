package com.bone.workflow.starter.adapter;

import com.bone.core.tenant.context.TenantContext;
import com.bone.workflow.domain.WorkflowInstance;
import com.bone.workflow.domain.WorkflowTask;
import com.bone.workflow.ports.spi.NotificationPort;
import com.bone.workflow.ports.spi.WorkflowIdGenerator;
import com.bone.workflow.ports.spi.WorkflowInboxQuery;
import com.bone.workflow.ports.spi.WorkflowNotification;
import com.bone.workflow.starter.metrics.WorkflowMetrics;
import com.bone.workflow.starter.sdk.WfNotificationRow;
import com.bone.workflow.starter.sdk.WfNotificationSdkRepository;
import com.bone.workflow.starter.spi.RoleMemberProvider;
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

  /**
   * 挂起告警的兜底接收人：{@code workflow-admin}。宿主可按部署改默认，或经 {@link #setAdminRecipientId(String)} 调整。
   *
   * <p><b>为什么只是兜底</b>：该账号在多数部署里并不存在（幽灵接收人），告警落它的收件箱等于无人看见。首选路径是 {@link #adminRoleCode} + {@link
   * RoleMemberProvider} 解析出的<b>真实成员</b>，本兜底仅在角色未配置或解析为空时启用。
   */
  private static final String DEFAULT_ADMIN_RECIPIENT = "workflow-admin";

  private final WfNotificationSdkRepository repository;
  private final WorkflowIdGenerator idGenerator;
  private final Clock clock;
  private final WorkflowMetrics metrics;

  private String adminRecipientId = DEFAULT_ADMIN_RECIPIENT;
  private String adminRoleCode;
  private RoleMemberProvider roleMemberProvider;

  public PersistenceNotificationPort(
      WfNotificationSdkRepository repository, WorkflowIdGenerator idGenerator) {
    this(repository, idGenerator, Clock.systemUTC(), WorkflowMetrics.NOOP);
  }

  public PersistenceNotificationPort(
      WfNotificationSdkRepository repository, WorkflowIdGenerator idGenerator, Clock clock) {
    this(repository, idGenerator, clock, WorkflowMetrics.NOOP);
  }

  /** 完整构造：注入可观测指标（P1-1 / P0-3 告警失败计数）。 */
  public PersistenceNotificationPort(
      WfNotificationSdkRepository repository,
      WorkflowIdGenerator idGenerator,
      Clock clock,
      WorkflowMetrics metrics) {
    this.repository = repository;
    this.idGenerator = idGenerator;
    this.clock = clock == null ? Clock.systemUTC() : clock;
    this.metrics = metrics == null ? WorkflowMetrics.NOOP : metrics;
  }

  /** 调整挂起告警的<b>兜底</b>接收人（默认为 {@code workflow-admin}）。 */
  public void setAdminRecipientId(String adminRecipientId) {
    if (adminRecipientId != null && !adminRecipientId.isBlank()) {
      this.adminRecipientId = adminRecipientId;
    }
  }

  /**
   * 配置挂起告警的角色接收人：告警优先发给该角色的<b>真实成员</b>（经 {@link RoleMemberProvider} 解析）。
   *
   * <p>由自动装配按 {@code bone.workflow.notify.admin-role} 属性注入；未配置或 SPI 未接入时回落 {@link
   * #setAdminRecipientId(String)} 的兜底接收人，并打 WARN（告警不静默丢，但接收人质量降级要可诊断）。
   */
  public void setAdminRole(String adminRoleCode, RoleMemberProvider roleMemberProvider) {
    if (adminRoleCode != null && !adminRoleCode.isBlank()) {
      this.adminRoleCode = adminRoleCode;
      this.roleMemberProvider = roleMemberProvider;
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
    // 挂起 = 引擎已停下等人工介入，告警是唯一把人叫回来的手段——发给真实成员，而不是没人认领的幽灵账号。
    for (String recipient : resolveAdminRecipients()) {
      write(
          recipient,
          "SUSPENDED",
          instance,
          instance.currentNodeCode(),
          "审批流程挂起，需人工介入",
          "单据 " + instance.entityCode() + "/" + instance.recordId() + " 挂起：" + safe(reason),
          "workflow-engine");
    }
  }

  /**
   * 挂起告警接收人解析：首选 {@code bone.workflow.notify.admin-role} 配置的角色成员（真实账户），回落兜底接收人。
   *
   * <p>回落必打 WARN——告警落进无人认领的兜底收件箱等于「挂起无人发现、流程静默死掉」，降级必须可诊断， 但告警本身绝不丢（仍落库，运维可对账）。
   */
  private List<String> resolveAdminRecipients() {
    if (adminRoleCode == null || roleMemberProvider == null) {
      org.slf4j.LoggerFactory.getLogger(PersistenceNotificationPort.class)
          .warn(
              "[workflow][notify] 挂起告警未配置角色接收人（bone.workflow.notify.admin-role）或 RoleMemberProvider 未接入，"
                  + "回落兜底接收人 {}——请配置角色，否则挂起单据无人认领",
              adminRecipientId);
      return List.of(adminRecipientId);
    }
    List<String> members = roleMemberProvider.accountIdsByRoleCode(adminRoleCode);
    if (members == null || members.isEmpty()) {
      org.slf4j.LoggerFactory.getLogger(PersistenceNotificationPort.class)
          .warn(
              "[workflow][notify] 挂起告警角色 {} 解析不出成员（角色不存在或未分配人员），回落兜底接收人 {}",
              adminRoleCode,
              adminRecipientId);
      return List.of(adminRecipientId);
    }
    return members.stream()
        .filter(m -> m != null && !m.isBlank())
        .distinct()
        .collect(Collectors.toList());
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
  public void notifyTaskCancelled(WorkflowTask task, WorkflowInstance instance, String reason) {
    // 通知「被作废的办理人」：他的待办会凭空消失，必须告知去向，否则只能靠刷待办列表猜。
    write(
        task.assigneeId(),
        "TASK_CANCELLED",
        instance,
        task.nodeCode(),
        "待办已取消，无需处理",
        "单据 " + instance.entityCode() + "/" + instance.recordId() + " 的待办已取消：" + safe(reason),
        "workflow-engine");
  }

  @Override
  public void notifyCc(
      WorkflowInstance instance, String nodeCode, java.util.List<String> recipients) {
    if (recipients == null || recipients.isEmpty()) {
      return;
    }
    // 抄送是「知会」不是「待办」：逐人写一条 CC 站内信，不建任务、不参与节点完成判据。
    for (String recipient : recipients) {
      if (recipient == null || recipient.isBlank()) {
        continue;
      }
      write(
          recipient,
          "CC",
          instance,
          nodeCode,
          "单据抄送（仅知会，无需审批）",
          "单据 " + instance.entityCode() + "/" + instance.recordId() + " 抄送给您知悉",
          "workflow-engine");
    }
  }

  @Override
  public List<WorkflowNotification> inbox(long tenantId, String recipientId, boolean unreadOnly) {
    List<WfNotificationRow> rows =
        unreadOnly
            ? repository.selectInboxUnread(tenantId, recipientId)
            : repository.selectInboxAll(tenantId, recipientId);
    return rows.stream().map(PersistenceNotificationPort::toDomain).collect(Collectors.toList());
  }

  /**
   * 挂起原因（诊断）：读回引擎挂起时写入的告警正文。
   *
   * <p>读取失败一律降级为 {@link Optional#empty()}——诊断信息属于「锦上添花」，绝不能因为它查不到就让单据详情 500。
   */
  @Override
  public java.util.Optional<String> latestSuspensionNotice(long tenantId, String instanceId) {
    if (instanceId == null || instanceId.isBlank()) {
      return java.util.Optional.empty();
    }
    try {
      WfNotificationRow row =
          repository.selectLatestByInstanceAndType(tenantId, instanceId, "SUSPENDED");
      if (row == null || row.getBody() == null || row.getBody().isBlank()) {
        return java.util.Optional.empty();
      }
      return java.util.Optional.of(row.getBody());
    } catch (RuntimeException ex) {
      org.slf4j.LoggerFactory.getLogger(PersistenceNotificationPort.class)
          .warn("[workflow] 读取挂起原因失败（降级为通用指引）：instance={} err={}", instanceId, ex.toString());
      return java.util.Optional.empty();
    }
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
      metrics.notificationSent(type);
      // P0-3：挂起告警是 fail-closed 闭环的唯一召回手段，专项计数，失败绝不只 WARN 看不见。
      if ("SUSPENDED".equals(type)) {
        metrics.suspendedAlertSent();
      }
    } catch (RuntimeException ex) {
      // 通知失败绝不阻断审批主链路（双保险；引擎侧也已包 try-catch）。
      metrics.notificationFailed();
      if ("SUSPENDED".equals(type)) {
        metrics.suspendedAlertFailed();
      }
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
