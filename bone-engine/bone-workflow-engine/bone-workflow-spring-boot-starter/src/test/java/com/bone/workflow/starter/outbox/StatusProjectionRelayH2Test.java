package com.bone.workflow.starter.outbox;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.bone.core.tenant.context.TenantContext;
import com.bone.workflow.starter.sdk.WorkflowSdkTestContext;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * 状态投影中继的端到端测试（H2 经 SDK {@code @Sql} 代理真实执行生产 SQL）。
 *
 * <p><b>为什么必须真库</b>：中继的正确性全在「取哪些 / 按什么顺序 / 失败后还能不能取到」上，这些都由 SQL 决定。 内存 stub 测不出「FAILED 且
 * next_retry_at 未到就不该被取到」这类判据。
 *
 * <p><b>判别性设计（防假绿）</b>：每个用例都构造「中继逻辑写错就会失败」的数据布局——尤其是 {@link
 * #sameInstance_intentsAppliedInOrder()}：若中继不按序落地，先写 REJECTED 后写 APPROVED 会把终态投影颠倒。
 */
class StatusProjectionRelayH2Test {

  private static final long TENANT = 1L;

  private AnnotationConfigApplicationContext context;
  private JdbcTemplate jdbc;
  private WorkflowStatusOutboxRepository outbox;
  private StatusProjectionRelay relay;
  private final List<String> applied = new ArrayList<>();
  private final AtomicInteger idSeq = new AtomicInteger(5000);

  @BeforeEach
  void setUp() {
    TenantContext.setTenantId(TENANT);
    context = WorkflowSdkTestContext.create();
    jdbc = context.getBean(JdbcTemplate.class);
    outbox = context.getBean(WorkflowStatusOutboxRepository.class);
    // 记录型 executor：把每次执行追加到 applied，可按要求抛错。
    // 契约与真实 RuntimeActionIntentExecutor 一致：actionCode 为 null（SUSPENDED / RESUMED）时不执行业务动作。
    StatusIntentExecutor executor =
        intent -> {
          if (intent.actionCode() == null) {
            return;
          }
          if ("FAIL_ME".equals(intent.paramsJson())) {
            throw new IllegalStateException("模拟平台动作失败");
          }
          applied.add(intent.instanceId() + ":" + intent.intent());
        };
    relay = new StatusProjectionRelay(outbox, executor, 100, 3);
  }

  @AfterEach
  void tearDown() {
    TenantContext.clear();
    if (context != null) {
      context.close();
    }
  }

  @Test
  @DisplayName("意图入箱 → 中继执行 → 标记 DONE")
  void intentIsRelayedAndMarkedDone() {
    enqueue("i1", "APPROVED", "approve", null);
    assertEquals(1, outbox.pollDue(10).size(), "入箱后应可取到");

    assertEquals(1, relay.runOnce(10));
    assertEquals(List.of("i1:APPROVED"), applied);
    assertEquals(0, outbox.pollDue(10).size(), "已 DONE 不得再被取到");
    assertEquals(1, countByState("DONE"));
  }

  @Test
  @DisplayName("无动作意图（SUSPENDED）入箱即视为已处理，不调用执行器")
  void actionlessIntentIsMarkedDoneWithoutExecutor() {
    enqueue("i1", "SUSPENDED", null, "审批人解析不出");
    assertEquals(1, relay.runOnce(10));
    assertEquals(0, applied.size(), "SUSPENDED 无业务动作，不应调用执行器");
    assertEquals(1, countByState("DONE"));
  }

  @Test
  @DisplayName("执行失败 → 标记 FAILED 并按退避排期；未到时刻不得被取到")
  void failureIsScheduledWithBackoff_andNotDueYet() {
    enqueue("i1", "REJECTED", "reject", "FAIL_ME");

    assertEquals(0, relay.runOnce(10), "执行失败不得计入成功数");
    assertEquals(1, countByState("FAILED"));
    assertEquals(0, applied.size());

    // 退避未到：本轮不得再取到（否则会立刻重试，退避形同虚设）
    assertEquals(0, outbox.pollDue(10).size(), "退避未到不应被取到");

    // 手工把 next_retry_at 拨到过去，模拟退避已到
    jdbc.update(
        "UPDATE wf_status_outbox SET next_retry_at = ? WHERE tenant_id = ?",
        java.sql.Timestamp.from(java.time.Instant.now().minusSeconds(60)),
        TENANT);
    assertEquals(1, outbox.pollDue(10).size(), "退避已到应可重试");
  }

  @Test
  @DisplayName("重试达上限后不再排期（留在 FAILED），中继不得无限空转")
  void exhaustedRetriesStopBeingScheduled() {
    enqueue("i1", "REJECTED", "reject", "FAIL_ME");
    // maxRetries=3：前 3 次排期，第 4 次起不再排期
    for (int round = 0; round < 3; round++) {
      relay.runOnce(10);
      jdbc.update("UPDATE wf_status_outbox SET next_retry_at = NULL WHERE tenant_id = ?", TENANT);
    }
    relay.runOnce(10);

    Integer retryCount =
        jdbc.queryForObject(
            "SELECT retry_count FROM wf_status_outbox WHERE tenant_id = ?", Integer.class, TENANT);
    assertEquals(3, retryCount, "应重试满 3 次");
    assertTrue(
        jdbc.queryForObject(
                "SELECT next_retry_at FROM wf_status_outbox WHERE tenant_id = ?",
                java.sql.Timestamp.class,
                TENANT)
            == null,
        "达上限后 next_retry_at 必须为 NULL（不再排期）");
  }

  @Test
  @DisplayName("同实例意图必须按序落地：前一条失败时，本批次跳过该实例后续意图")
  void sameInstance_intentsAppliedInOrder() {
    enqueue("i1", "REJECTED", "reject", "FAIL_ME"); // 先写、且会失败
    enqueue("i1", "APPROVED", "approve", null); // 后写、本会成功

    relay.runOnce(10);

    assertEquals(0, applied.size(), "前一条失败时，后一条不得抢先把终态投影颠倒");
    assertEquals(1, countByState("FAILED"));
    assertEquals(1, countByState("NEW"), "被跳过的后续意图应留在 NEW 等下轮");
  }

  @Test
  @DisplayName("一个实例卡住不阻塞其它实例的投影")
  void oneStuckInstanceDoesNotBlockOthers() {
    enqueue("stuck", "REJECTED", "reject", "FAIL_ME");
    enqueue("healthy", "APPROVED", "approve", null);

    assertEquals(1, relay.runOnce(10), "健康的实例必须照常落地");
    assertEquals(List.of("healthy:APPROVED"), applied);
  }

  @Test
  @DisplayName("终态实例意图行完全缺失 ⇒ 对账可查出（意图丢失场景）")
  void reconciliationDetectsLostProjection() {
    // 造一个已 APPROVED 的实例，但没有任何 APPROVED 意图行（模拟崩溃在 phase CAS 与写意图之间）
    jdbc.update(
        "INSERT INTO wf_instance (id, tenant_id, entity_code, record_id, def_key, def_version,"
            + " current_node_code, phase, started_by, started_at, node_round, node_entered_at,"
            + " reject_rounds, updated_at, created_by, created_at, updated_by, deleted, version)"
            + " VALUES (700, ?, 'expense_reimbursement', 'r-9', 'expense', 1, 'end', 'APPROVED',"
            + " 'u1', NOW(), 0, NOW(), 0, NOW(), 'u1', NOW(), 'u1', 0, 0)",
        TENANT);

    List<StatusIntent> missing =
        outbox.findTerminalMissingProjection(10, WorkflowStatusOutboxRepository.RECONCILE_WINDOW);
    assertEquals(1, missing.size(), "意图丢失必须能被对账查出，否则「引擎说批了、单据没动」无人知晓");
    assertEquals("APPROVED", missing.get(0).intent());
    assertEquals("approve", missing.get(0).actionCode());
  }

  @Test
  @DisplayName("负向：已有 FAILED 意图行不得被对账重复补投（否则有上限重试变无限重试）")
  void reconciliation_skipsInstanceWithFailedIntentRow() {
    givenTerminalInstance("710", "APPROVED");
    // 意图已存在但执行失败、重试已达上限留在 FAILED——按设计转人工，对账不得再补投
    enqueue("710", "APPROVED", "approve", null);
    jdbc.update(
        "UPDATE wf_status_outbox SET state = 'FAILED', retry_count = 99, next_retry_at = NULL"
            + " WHERE tenant_id = ? AND instance_id = ?",
        TENANT,
        "710");

    List<StatusIntent> missing =
        outbox.findTerminalMissingProjection(10, WorkflowStatusOutboxRepository.RECONCILE_WINDOW);
    assertEquals(0, missing.size(), "意图行已在（FAILED 亦算）——若判据是「没有 DONE 行」，每轮对账都会重复补投，把有上限重试变成无限重试");
  }

  @Test
  @DisplayName("已有 DONE 意图行不得被对账补投（投影已落地的证据行）")
  void reconciliation_skipsInstanceWithDoneIntentRow() {
    givenTerminalInstance("720", "APPROVED");
    enqueue("720", "APPROVED", "approve", null);
    relay.runOnce(10);
    assertEquals(1, countByState("DONE"), "前置条件：投影已落地");

    List<StatusIntent> missing =
        outbox.findTerminalMissingProjection(10, WorkflowStatusOutboxRepository.RECONCILE_WINDOW);
    assertEquals(0, missing.size(), "DONE 行即投影证据，不得重判缺投影——否则重复执行业务动作");
  }

  @Test
  @DisplayName("负向：超出对账窗口的终态实例不补投（DONE 行按保留期清理，窗口防重投影）")
  void reconciliation_ignoresInstancesOutsideWindow() {
    givenTerminalInstance("730", "APPROVED");
    // 把实例终态时间拨到 7 天前（超出 6h 对账窗口）：其 DONE 证据行可能已被清理，不得再补投
    jdbc.update(
        "UPDATE wf_instance SET updated_at = ? WHERE id = ? AND tenant_id = ?",
        java.sql.Timestamp.from(java.time.Instant.now().minus(java.time.Duration.ofDays(7))),
        "730",
        TENANT);

    List<StatusIntent> missing =
        outbox.findTerminalMissingProjection(10, WorkflowStatusOutboxRepository.RECONCILE_WINDOW);
    assertEquals(0, missing.size(), "窗口外实例的 DONE 行可能已清，不设窗口会永久重判缺投影 ⇒ 重复执行业务动作");
  }

  @Test
  @DisplayName("对账补投端到端：丢失的意图被补写入箱并由中继落地，重复对账不重复补投")
  void reconcile_insertsLostIntent_andRelayLandsIt() {
    givenTerminalInstance("740", "REJECTED");

    assertEquals(1, relay.runReconcile(10), "意图丢失必须被补投");
    assertEquals(1, outbox.pollDue(10).size(), "补投的意图应可被中继取到");
    assertEquals(0, relay.runReconcile(10), "重复对账不得重复补投（确定性 id 撞主键即跳过）");

    assertEquals(1, relay.runOnce(10), "补投的意图必须能被中继执行落地");
    assertEquals(List.of("740:REJECTED"), applied);
    assertEquals(1, countByState("DONE"));
  }

  @Test
  @DisplayName("DONE 行按保留期物理清理：超期行被删、窗口内行保留（保留期 > 对账窗口）")
  void purgeDone_removesOnlyExpiredRows() {
    enqueue("old", "APPROVED", "approve", null);
    enqueue("fresh", "APPROVED", "approve", null);
    relay.runOnce(10);
    assertEquals(2, countByState("DONE"), "前置条件：两条均已落地");
    // 把 old 的落地时间拨到 8 天前（超过 7 天保留期）
    jdbc.update(
        "UPDATE wf_status_outbox SET updated_at = ? WHERE instance_id = ? AND tenant_id = ?",
        java.sql.Timestamp.from(java.time.Instant.now().minus(java.time.Duration.ofDays(8))),
        "old",
        TENANT);

    int purged = outbox.purgeDone(WorkflowStatusOutboxRepository.DONE_RETENTION);
    assertEquals(1, purged, "只应清理超期的 DONE 行");
    assertEquals(1, countByState("DONE"), "保留期内的 DONE 行（投影证据）不得被清——否则对账重判缺投影");
    assertTrue(
        WorkflowStatusOutboxRepository.DONE_RETENTION.compareTo(
                WorkflowStatusOutboxRepository.RECONCILE_WINDOW)
            > 0,
        "保留期必须大于对账窗口，否则窗口内实例的 DONE 行被清 ⇒ 重复执行业务动作");
  }

  private void givenTerminalInstance(String instanceId, String phase) {
    jdbc.update(
        "INSERT INTO wf_instance (id, tenant_id, entity_code, record_id, def_key, def_version,"
            + " current_node_code, phase, started_by, started_at, node_round, node_entered_at,"
            + " reject_rounds, updated_at, created_by, created_at, updated_by, deleted, version)"
            + " VALUES (?, ?, 'expense_reimbursement', ?, 'expense', 1, 'end', ?,"
            + " 'u1', NOW(), 0, NOW(), 0, NOW(), 'u1', NOW(), 'u1', 0, 0)",
        instanceId,
        TENANT,
        "r-" + instanceId,
        phase);
  }

  @Test
  @DisplayName("无租户上下文的调度线程：中继按行内 tenant_id 重建上下文并正常落地")
  void schedulerThreadWithoutTenantContext_relaysByRowTenant() {
    enqueue("i1", "APPROVED", "approve", null);
    enqueue("i2", "REJECTED", "reject", null);

    // 模拟 @Scheduled 线程：清掉租户上下文再跑
    TenantContext.clear();
    assertNull(TenantContext.getTenantIdAsLong(), "前置条件：本线程不得有租户上下文（否则本用例失去判别力）");

    assertEquals(2, relay.runOnce(10), "无上下文线程也必须能把全部租户的意图落地");
    assertEquals(2, countByState("DONE"));
    assertNull(TenantContext.getTenantIdAsLong(), "跑完后必须恢复现场（clear），不得把租户上下文泄漏给后续任务");
  }

  private void enqueue(String instanceId, String intent, String actionCode, String paramsJson) {
    outbox.insert(
        new StatusIntent(
            String.valueOf(idSeq.incrementAndGet()),
            instanceId,
            "expense_reimbursement",
            "r-" + instanceId,
            TENANT,
            "u-approver",
            intent,
            actionCode,
            paramsJson,
            "NEW",
            0,
            Instant.now()));
  }

  private int countByState(String state) {
    Integer count =
        jdbc.queryForObject(
            "SELECT COUNT(*) FROM wf_status_outbox WHERE tenant_id = ? AND state = ?",
            Integer.class,
            TENANT,
            state);
    return count == null ? 0 : count;
  }
}
