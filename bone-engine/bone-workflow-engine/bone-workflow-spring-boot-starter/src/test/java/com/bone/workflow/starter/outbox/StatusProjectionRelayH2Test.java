package com.bone.workflow.starter.outbox;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.bone.core.tenant.context.TenantContext;
import com.bone.workflow.starter.metrics.WorkflowMetrics;
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
  @DisplayName("重试达上限后必须落终态死信：不得被超时 CLAIMED 抢占路径每 5 分钟反复执行，且对死信存量可见")
  void exhaustedRetriesStopBeingScheduled() {
    // 计数型 executor：统计「永久失败的动作」被真正执行了几次 —— 这是「中继是否空转」的直接判据。
    AtomicInteger execCount = new AtomicInteger();
    io.micrometer.core.instrument.simple.SimpleMeterRegistry registry =
        new io.micrometer.core.instrument.simple.SimpleMeterRegistry();
    WorkflowMetrics metrics = new WorkflowMetrics(registry);
    StatusIntentExecutor alwaysFails =
        intent -> {
          execCount.incrementAndGet();
          throw new IllegalStateException("模拟动作定义缺失（永久失败）");
        };
    StatusProjectionRelay counted = new StatusProjectionRelay(outbox, alwaysFails, 100, 3, metrics);

    enqueue("i1", "REJECTED", "reject", "FAIL_ME");
    // maxRetries=3：前 3 次失败后排期重试，第 4 次达上限
    for (int round = 0; round < 3; round++) {
      counted.runOnce(10);
      jdbc.update("UPDATE wf_status_outbox SET next_retry_at = NULL WHERE tenant_id = ?", TENANT);
    }
    counted.runOnce(10); // 第 4 次：达上限，必须落终态

    Integer retryCount =
        jdbc.queryForObject(
            "SELECT retry_count FROM wf_status_outbox WHERE tenant_id = ?", Integer.class, TENANT);
    assertEquals(3, retryCount, "应重试满 3 次");
    assertEquals(4, execCount.get(), "前置条件：3 次排期尝试 + 1 次达上限尝试，共执行 4 次");

    // 判据 1：必须落 state=FAILED。（原实现停在 claim() 写入的 CLAIMED）
    String state =
        jdbc.queryForObject(
            "SELECT state FROM wf_status_outbox WHERE tenant_id = ?", String.class, TENANT);
    assertEquals("FAILED", state, "达上限后必须是终态 FAILED，而不是停留在 claim() 写入的 CLAIMED");

    // 判据 2：终态必须对死信存量可见。（原实现该行是 CLAIMED ⇒ countTerminallyFailed 恒为 0）
    assertEquals(1, outbox.countTerminallyFailed(), "终态死信必须对死信存量监控可见，否则「转人工」无人知晓");

    // 判据 3（真实事故直接复现）：把 updated_at 拨到 6 分钟前，模拟 CLAIM_TIMEOUT（5 分钟）已过。
    //   原实现：该行是 CLAIMED 且已超时 ⇒ 被「超时 CLAIMED 抢占」重新取回、再次执行永久失败的动作，
    //   表现为 ERROR 每 5 分钟刷一次且永不收敛。
    jdbc.update(
        "UPDATE wf_status_outbox SET updated_at = ? WHERE tenant_id = ?",
        java.sql.Timestamp.from(Instant.now().minusSeconds(6 * 60)),
        TENANT);
    assertEquals(0, outbox.pollDue(10).size(), "终态死信不得被超时 CLAIMED 抢占路径重新取回");
    counted.runOnce(10);
    assertEquals(4, execCount.get(), "终态死信不得再被执行（原实现每 5 分钟重复执行一次永久失败的动作）");

    // 判据 4：达上限必须计入 terminally_failed（与 P0-2 终态口径一致）；
    //   前 3 次「会重试」的失败各计一次 failed，第 4 次转终态只计 terminally_failed
    assertEquals(
        1.0,
        counterValue(registry, "bone.workflow.relay.intents", "result", "terminally_failed"),
        0.001,
        "达上限应计入 terminally_failed");
    assertEquals(
        3.0,
        counterValue(registry, "bone.workflow.relay.intents", "result", "failed"),
        0.001,
        "前 3 次排期重试的失败各计一次 failed，第 4 次转终态不得重复计入 failed");
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

  // ── P0-2 幂等硬约束（中继层 fail-closed）───────────────────────────────

  @Test
  @DisplayName("P0-2 硬约束：未声明幂等的动作被中继拒绝执行并落终态 FAILED，且不重投、绝不重执")
  void nonIdempotentAction_refusedAndTerminallyFailed() {
    java.util.concurrent.atomic.AtomicInteger invoked =
        new java.util.concurrent.atomic.AtomicInteger();
    com.bone.workflow.starter.spi.WorkflowActionIdempotencyPolicy policy =
        (entity, tenant, action) -> false; // 该动作未声明幂等
    RuntimeActionIntentExecutor guardedExecutor =
        new RuntimeActionIntentExecutor(
            (e, t, r, a, p, o, k, v) -> invoked.incrementAndGet(), policy, WorkflowMetrics.NOOP);
    StatusProjectionRelay guardedRelay = new StatusProjectionRelay(outbox, guardedExecutor, 100, 3);

    enqueue("i1", "APPROVED", "approve", null);

    assertEquals(0, guardedRelay.runOnce(10), "拒绝执行的意图不得计入成功数");
    assertEquals(0, invoked.get(), "动作绝不应被调用——at-least-once 重投会双推进业务状态");
    assertEquals(1, countByState("FAILED"), "必须落终态 FAILED 转人工介入");

    // 终态 FAILED 的 next_retry_at 被推到远未来，pollDue 永不应再取到 ⇒ 不会重投执行
    assertEquals(0, outbox.pollDue(10).size(), "终态失败不得被重新抢占执行（重投是危险源）");

    // 即便有人手工把 next_retry_at 拨回过去，执行器层仍会拒绝（双保险，不依赖调度不重取）
    jdbc.update(
        "UPDATE wf_status_outbox SET next_retry_at = ? WHERE tenant_id = ? AND instance_id = ?",
        java.sql.Timestamp.from(java.time.Instant.now().minusSeconds(60)),
        TENANT,
        "i1");
    guardedRelay.runOnce(10);
    assertEquals(0, invoked.get(), "终态失败即便被重取，执行器仍拒绝执行——双保险，杜绝双推进");
  }

  @Test
  @DisplayName("P0-2：动作已声明幂等时中继照常执行（硬约束不影响合法动作）")
  void idempotentAction_executesThroughRelay() {
    java.util.concurrent.atomic.AtomicInteger invoked =
        new java.util.concurrent.atomic.AtomicInteger();
    com.bone.workflow.starter.spi.WorkflowActionIdempotencyPolicy policy =
        (entity, tenant, action) -> true; // 已声明幂等
    RuntimeActionIntentExecutor guardedExecutor =
        new RuntimeActionIntentExecutor(
            (e, t, r, a, p, o, k, v) -> invoked.incrementAndGet(), policy, WorkflowMetrics.NOOP);
    StatusProjectionRelay guardedRelay = new StatusProjectionRelay(outbox, guardedExecutor, 100, 3);

    enqueue("i2", "APPROVED", "approve", null);
    assertEquals(1, guardedRelay.runOnce(10));
    assertEquals(1, invoked.get(), "已声明幂等的动作必须正常推进业务状态");
    assertEquals(1, countByState("DONE"));
  }

  // ── P1-1 可观测指标 ──────────────────────────────────────────────

  @Test
  @DisplayName("P1-1：有 MeterRegistry 时，终态失败与成功落地均被计数")
  void metrics_countExecutedAndTerminallyFailed() {
    io.micrometer.core.instrument.simple.SimpleMeterRegistry registry =
        new io.micrometer.core.instrument.simple.SimpleMeterRegistry();
    WorkflowMetrics metrics = new WorkflowMetrics(registry);

    RuntimeActionIntentExecutor refused =
        new RuntimeActionIntentExecutor(
            (e, t, r, a, p, o, k, v) -> {}, (e, t, a) -> false, metrics);
    StatusProjectionRelay relayRefused =
        new StatusProjectionRelay(outbox, refused, 100, 3, metrics);
    enqueue("m1", "APPROVED", "approve", null);
    relayRefused.runOnce(10);

    RuntimeActionIntentExecutor ok =
        new RuntimeActionIntentExecutor((e, t, r, a, p, o, k, v) -> {}, (e, t, a) -> true, metrics);
    StatusProjectionRelay relayOk = new StatusProjectionRelay(outbox, ok, 100, 3, metrics);
    enqueue("m2", "APPROVED", "approve", null);
    relayOk.runOnce(10);

    assertEquals(
        1.0,
        counterValue(registry, "bone.workflow.relay.intents", "result", "terminally_failed"),
        0.001,
        "非幂等动作拒绝须计入 terminally_failed");
    assertEquals(
        1.0,
        counterValue(registry, "bone.workflow.relay.intents", "result", "executed"),
        0.001,
        "正常落地须计入 executed");
  }

  @Test
  @DisplayName("P1-1 完善：积压深度 Gauge 反映待处理意图数；单条意图耗时 Timer 被记录")
  void metrics_pendingDepthGaugeAndIntentDurationTimer() {
    io.micrometer.core.instrument.simple.SimpleMeterRegistry registry =
        new io.micrometer.core.instrument.simple.SimpleMeterRegistry();
    WorkflowMetrics metrics = new WorkflowMetrics(registry);
    StatusIntentExecutor recording = intent -> {};
    StatusProjectionRelay relay = new StatusProjectionRelay(outbox, recording, 100, 3, metrics);

    enqueue("g1", "APPROVED", "approve", null);
    enqueue("g2", "REJECTED", "reject", null);
    assertEquals(2, outbox.countPendingDue(), "前置：两条待处理");

    relay.runOnce(10);

    assertEquals(
        2.0,
        gaugeValue(registry, "bone.workflow.outbox.pending_depth"),
        0.001,
        "积压深度 Gauge 须反映本轮取到的到期意图数");
    assertTrue(
        timerCount(registry, "bone.workflow.relay.intent.duration") >= 1,
        "至少一条意图执行，单条耗时 Timer 须被记录");
  }

  @Test
  @DisplayName("P1-1 完善：countPendingDue 与 pollDue 同判据（DONE 后不再计入积压）")
  void countPendingDue_matchesPollDue() {
    enqueue("c1", "APPROVED", "approve", null);
    assertEquals(1, outbox.countPendingDue(), "入箱后积压须为 1");
    relay.runOnce(10);
    assertEquals(0, outbox.countPendingDue(), "DONE 后不再计入积压");
    assertEquals(0, outbox.pollDue(10).size());
  }

  @Test
  @DisplayName("P0-2 完善：countTerminallyFailed 反映死信存量（拒执后被终态落库）")
  void deadLetterDepth_reflectsTerminallyFailed() {
    assertEquals(0, outbox.countTerminallyFailed(), "前置：无死信");
    com.bone.workflow.starter.spi.WorkflowActionIdempotencyPolicy policy = (e, t, a) -> false;
    RuntimeActionIntentExecutor refused =
        new RuntimeActionIntentExecutor(
            (e, t, r, a, p, o, k, v) -> {}, policy, WorkflowMetrics.NOOP);
    StatusProjectionRelay relay = new StatusProjectionRelay(outbox, refused, 100, 3);
    enqueue("dl1", "APPROVED", "approve", null);
    relay.runOnce(10);
    assertEquals(1, outbox.countTerminallyFailed(), "终态拒绝后死信存量须为 1");
  }

  private double counterValue(
      io.micrometer.core.instrument.simple.SimpleMeterRegistry registry,
      String name,
      String tagKey,
      String tagValue) {
    io.micrometer.core.instrument.Counter counter =
        registry.find(name).tag(tagKey, tagValue).counter();
    return counter == null ? 0.0 : counter.count();
  }

  private double gaugeValue(
      io.micrometer.core.instrument.simple.SimpleMeterRegistry registry, String name) {
    io.micrometer.core.instrument.Gauge gauge = registry.find(name).gauge();
    return gauge == null ? -1.0 : gauge.value();
  }

  private double timerCount(
      io.micrometer.core.instrument.simple.SimpleMeterRegistry registry, String name) {
    io.micrometer.core.instrument.Timer timer = registry.find(name).timer();
    return timer == null ? 0.0 : timer.count();
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
