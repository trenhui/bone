package com.bone.workflow.starter.adapter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;

import com.bone.core.tenant.context.TenantContext;
import com.bone.workflow.domain.WorkflowInstance;
import com.bone.workflow.domain.WorkflowInstance.Phase;
import com.bone.workflow.domain.WorkflowTask;
import com.bone.workflow.domain.WorkflowTask.Status;
import com.bone.workflow.ports.spi.WorkflowIdGenerator;
import com.bone.workflow.ports.spi.WorkflowNotification;
import com.bone.workflow.starter.sdk.WfNotificationSdkRepository;
import com.bone.workflow.starter.sdk.WorkflowSdkTestContext;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

/**
 * {@link PersistenceNotificationPort} 的站内信落库 + 收件箱查询测试（H2 原样执行生产 SQL）。
 *
 * <p>验证：通知写入 {@code wf_notification}、收件箱可按接收人/未读过滤查询、租户随 {@link TenantContext} 落地。
 */
class PersistenceNotificationPortTest {

  private static final long TENANT = 7L;

  private AnnotationConfigApplicationContext context;
  private WfNotificationSdkRepository repository;
  private PersistenceNotificationPort port;
  private final AtomicInteger idSeq = new AtomicInteger();

  @BeforeEach
  void setUp() {
    TenantContext.setTenantId(TENANT);
    context = WorkflowSdkTestContext.create();
    repository = context.getBean(WfNotificationSdkRepository.class);
    WorkflowIdGenerator idGenerator = () -> "N" + idSeq.incrementAndGet();
    port = new PersistenceNotificationPort(repository, idGenerator);
  }

  @AfterEach
  void tearDown() {
    TenantContext.clear();
    if (context != null) {
      context.close();
    }
  }

  private WorkflowTask task(String assignee) {
    return new WorkflowTask(
        "t1", "i1", "approve", assignee, Status.PENDING, null, null, null, Instant.now(), null);
  }

  private WorkflowInstance instance() {
    return new WorkflowInstance(
        "i1",
        "expense",
        "r1",
        "expense",
        1,
        "approve",
        Phase.RUNNING,
        "u1",
        Instant.now(),
        Instant.now());
  }

  @Test
  @DisplayName("派单通知写入收件箱，办理人可查到未读待办通知")
  void taskCreated_writesInboxNotification() {
    port.notifyTaskCreated(task("mgr1"), instance());

    List<WorkflowNotification> inbox = port.inbox(TENANT, "mgr1", false);
    assertEquals(1, inbox.size());
    WorkflowNotification n = inbox.get(0);
    assertEquals("TASK_CREATED", n.type());
    assertEquals("mgr1", n.recipientId());
    assertEquals("i1", n.instanceId());
    assertEquals("expense", n.entityCode());
    assertEquals("r1", n.recordId());
    assertFalse(n.read());
  }

  @Test
  @DisplayName("挂起告警写入管理员收件箱（默认 workflow-admin）")
  void suspended_writesAdminNotification() {
    port.notifySuspended(instance(), "审批人解析不出");

    List<WorkflowNotification> admin = port.inbox(TENANT, "workflow-admin", false);
    assertEquals(1, admin.size());
    assertEquals("SUSPENDED", admin.get(0).type());
  }

  @Test
  @DisplayName("未读过滤：标记已读后仅未读查询不再返回")
  void unreadFilter_excludesRead() {
    port.notifyTaskCreated(task("mgr1"), instance());
    List<WorkflowNotification> all = port.inbox(TENANT, "mgr1", false);
    String id = all.get(0).id();
    Instant now = Instant.now();

    repository.markRead(id, TENANT, now, "mgr1");

    assertTrue(port.inbox(TENANT, "mgr1", true).isEmpty(), "仅未读查询应排除已读项");
    assertEquals(1, port.inbox(TENANT, "mgr1", false).size(), "全部查询仍含已读项");
  }

  @Test
  @DisplayName("挂起原因可取回：详情页据此展示「为什么卡住」，而不是只显示「已挂起」")
  void latestSuspensionNotice_returnsReason() {
    port.notifySuspended(instance(), "审批节点 approve 解析出的审批人含发起人本人（自审批，违反职责分离）");

    java.util.Optional<String> reason = port.latestSuspensionNotice(TENANT, "i1");

    assertTrue(reason.isPresent(), "挂起告警正文必须能读回");
    assertTrue(reason.get().contains("自审批"), "原因文案要能直接面向用户解释：" + reason.get());
  }

  @Test
  @DisplayName("无挂起记录时返回空（调用方降级为通用指引，不伪造原因）")
  void latestSuspensionNotice_emptyWhenNeverSuspended() {
    port.notifyTaskCreated(task("mgr1"), instance());

    assertTrue(port.latestSuspensionNotice(TENANT, "i1").isEmpty());
  }

  @Test
  @DisplayName("多次挂起取最新一条：指派后又挂起时不能展示上一次的旧原因")
  void latestSuspensionNotice_picksNewest() {
    port.notifySuspended(instance(), "第一次挂起：解析不出审批人");
    port.notifySuspended(instance(), "第二次挂起：审批人为发起人本人");

    String reason = port.latestSuspensionNotice(TENANT, "i1").orElse("");

    assertTrue(reason.contains("第二次"), "必须取最新一条，实际：" + reason);
  }

  @Test
  @DisplayName("租户隔离：另一租户收件箱为空")
  void tenantIsolation_otherTenantInboxEmpty() {
    port.notifyTaskCreated(task("mgr1"), instance());

    assertTrue(port.inbox(999L, "mgr1", false).isEmpty(), "跨租户不得串收件箱");
  }

  @Test
  @DisplayName("写库异常被吞掉（不阻断审批主链路）")
  void writeFailure_isSwallowed() {
    // 用 Mockito 替身让 insertNotification 抛错，验证端口吞异常、不向上抛
    WfNotificationSdkRepository faultyRepo = Mockito.mock(WfNotificationSdkRepository.class);
    Mockito.doThrow(new RuntimeException("db down"))
        .when(faultyRepo)
        .insertNotification(
            any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any());
    PersistenceNotificationPort faulty = new PersistenceNotificationPort(faultyRepo, () -> "N");

    // 不抛异常即通过：通知失败绝不阻断审批主链路
    faulty.notifyTaskCreated(task("mgr1"), instance());
  }
}
