package com.bone.workflow.starter;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.bone.core.tenant.context.TenantContext;
import com.bone.workflow.ports.spi.WorkflowInstanceRepository;
import com.bone.workflow.starter.sdk.WorkflowSdkTestContext;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * 卡死实例看门狗检测（H2 真实执行生产 SQL）。
 *
 * <p><b>判别性设计（防假绿）</b>：四个用例分别构造「应判卡死 / 有 PENDING 任务不判 / 刚进入不判 / 非 RUNNING 不判」四组数据， 任一判据写错（漏 NOT
 * EXISTS、阈值方向反、phase 守卫缺失）都会让断言失败。最后一条是<b>负向守卫</b>：有 PENDING 任务的实例绝不能被误判卡死。
 */
class WorkflowInstanceWatchdogH2Test {

  private static final long TENANT = 1L;

  private AnnotationConfigApplicationContext context;
  private JdbcTemplate jdbc;
  private WorkflowInstanceRepository repo;
  private final Instant cutoff = Instant.now().minus(Duration.ofHours(1));

  @BeforeEach
  void setUp() {
    TenantContext.setTenantId(TENANT);
    context = WorkflowSdkTestContext.create();
    jdbc = context.getBean(JdbcTemplate.class);
    repo = context.getBean(WorkflowInstanceRepository.class);
  }

  @AfterEach
  void tearDown() {
    TenantContext.clear();
    if (context != null) {
      context.close();
    }
  }

  @Test
  @DisplayName("RUNNING 且节点停留超阈值且无待办任务 ⇒ 判为卡死")
  void stuckInstance_withoutPendingTask_isDetected() {
    insertInstance("stuck1", "RUNNING", Instant.now().minus(Duration.ofHours(2)));
    List<String> stuck = repo.findStuckInstances(cutoff);
    assertTrue(stuck.contains("stuck1"), "节点停留超阈值且无待办的 RUNNING 实例必须被检出");
  }

  @Test
  @DisplayName("RUNNING 但仍有 PENDING 任务 ⇒ 不判为卡死（人在审批，非死实例）")
  void instanceWithPendingTask_isNotStuck() {
    insertInstance("hasPending", "RUNNING", Instant.now().minus(Duration.ofHours(2)));
    insertTask("t1", "hasPending", "PENDING");
    List<String> stuck = repo.findStuckInstances(cutoff);
    assertFalse(stuck.contains("hasPending"), "有 PENDING 任务的实例是正常等待审批，看门狗不应告警");
  }

  @Test
  @DisplayName("RUNNING 但节点刚进入（未超阈值）⇒ 不判为卡死，即使无待办")
  void recentlyEnteredRunningInstance_isNotStuck_evenWithoutTasks() {
    insertInstance("recent", "RUNNING", Instant.now().minus(Duration.ofMinutes(5)));
    List<String> stuck = repo.findStuckInstances(cutoff);
    assertFalse(stuck.contains("recent"), "节点停留未超阈值的实例不应被误判卡死");
  }

  @Test
  @DisplayName("非 RUNNING（已终态）⇒ 不判为卡死")
  void nonRunningPhase_isNotStuck() {
    insertInstance("approved", "APPROVED", Instant.now().minus(Duration.ofHours(2)));
    List<String> stuck = repo.findStuckInstances(cutoff);
    assertFalse(stuck.contains("approved"), "已终态实例不应进入卡死判定");
  }

  @Test
  @DisplayName("负向守卫：NOT EXISTS 判据存在——有 PENDING 任务的实例绝不被误报")
  void mutationGuard_pendingTaskExclusionHolds() {
    insertInstance("guard", "RUNNING", Instant.now().minus(Duration.ofHours(2)));
    insertTask("gt1", "guard", "PENDING");
    List<String> stuck = repo.findStuckInstances(cutoff);
    assertFalse(stuck.contains("guard"), "若 NOT EXISTS 判据失效，有 PENDING 的实例会被误判卡死");
  }

  private void insertInstance(String id, String phase, Instant nodeEnteredAt) {
    jdbc.update(
        "INSERT INTO wf_instance (id, tenant_id, entity_code, record_id, def_key, def_version,"
            + " current_node_code, phase, started_by, started_at, node_round, node_entered_at,"
            + " reject_rounds, updated_at, created_by, created_at, updated_by, deleted, version)"
            + " VALUES (?, ?, 'expense_reimbursement', ?, 'expense', 1, 'approve', ?, 'u1', NOW(), 0, ?,"
            + " 0, NOW(), 'u1', NOW(), 'u1', 0, 0)",
        id,
        TENANT,
        "r-" + id,
        phase,
        java.sql.Timestamp.from(nodeEnteredAt));
  }

  private void insertTask(String id, String instanceId, String status) {
    jdbc.update(
        "INSERT INTO wf_task (id, tenant_id, instance_id, node_code, assignee_id, status, decision,"
            + " comment, due_at, node_round, created_at, updated_at, created_by, updated_by, deleted,"
            + " version)"
            + " VALUES (?, ?, ?, 'approve', 'u1', ?, NULL, NULL, NULL, 0, NOW(), NOW(), 'u1', 'u1', 0, 0)",
        id,
        TENANT,
        instanceId,
        status);
  }
}
