package com.bone.workflow.starter.outbox;

import com.bone.core.tenant.context.TenantContext;
import com.bone.workflow.starter.metrics.WorkflowMetrics;
import java.time.Duration;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.scheduling.annotation.Scheduled;

/**
 * 状态投影中继：把发件箱里的意图真正落到业务单据状态上。
 *
 * <p><b>四条正确性纪律</b>：
 *
 * <ol>
 *   <li><b>同实例按序落地</b>：意图按 {@code created_at} 升序取（UUID 宿主下 id 字典序≠时间序），且 SQL 带<b>队头守卫</b>——
 *       同实例存在更早的在途意图时本条不返回。若某条失败，本批次内<b>跳过该实例的后续意图</b>—— 否则会先落地后写的 REJECTED 再落地先写的
 *       APPROVED，把终态投影颠倒。
 *   <li><b>失败指数退避、有上限</b>：重试间隔 1s→2s→4s…封顶 5 分钟；达到上限不再排期，留在 FAILED
 *       交由对账/人工。不能无限重试——一个永久失败的动作（如动作定义被删）会让中继永久空转。
 *   <li><b>不跨实例互堵</b>：一个实例卡住不影响其它实例的投影（本批次跳过的只是同一 instanceId）。
 *   <li><b>对账补投 + 定期清理</b>：{@link #runReconcile(int)} 补救「phase CAS 已写终态、意图行根本没落库」的丢失窗口；{@link
 *       #purgeDone()} 按保留期物理清理 DONE 行防表无限增长（保留期 &gt; 对账窗口，两者共同保证被清行不会被重投影）。
 * </ol>
 *
 * <p><b>调度</b>：{@link #runScheduled()} 带 {@code @Scheduled}，需宿主开启 {@code @EnableScheduling}；
 * 未开启时该注解被静默忽略（不影响启动），宿主也可自行按周期调用 {@link #runOnce(int)}。 建议周期 1–5 秒：状态投影是秒级最终一致，不必高频。
 *
 * <p><b>租户纪律</b>：{@code @Scheduled} 线程没有 TenantContext（ThreadLocal 不跨线程传播）。因此轮询<b>不按租户过滤</b>，
 * 每条意图执行 / 落标前按行内 {@code tenant_id} 重建上下文（{@code TenantContext.setTenantId}），结束后恢复原值——
 * 这样多租户意图都能落地，且每次平台动作调用都带正确的租户。若改成「按单租户过滤」，无上下文线程要么每 tick 抛错， 要么只投影某一个租户，其余租户的意图永久堆积而无人知晓。
 */
public class StatusProjectionRelay {

  private static final Logger log = LoggerFactory.getLogger(StatusProjectionRelay.class);

  /** 退避基数与封顶。 */
  private static final Duration BASE_BACKOFF = Duration.ofSeconds(1);

  private static final Duration MAX_BACKOFF = Duration.ofMinutes(5);

  private final WorkflowStatusOutboxRepository outbox;
  private final StatusIntentExecutor executor;
  private final int batchSize;
  private final int maxRetries;
  private final WorkflowMetrics metrics;

  /** 向后兼容（测试 / 旧装配）：不接指标。 */
  public StatusProjectionRelay(
      WorkflowStatusOutboxRepository outbox,
      StatusIntentExecutor executor,
      int batchSize,
      int maxRetries) {
    this(outbox, executor, batchSize, maxRetries, WorkflowMetrics.NOOP);
  }

  /** 完整构造：注入可观测指标（P1-1）。 */
  public StatusProjectionRelay(
      WorkflowStatusOutboxRepository outbox,
      StatusIntentExecutor executor,
      int batchSize,
      int maxRetries,
      WorkflowMetrics metrics) {
    this.outbox = outbox;
    this.executor = executor;
    this.batchSize = Math.max(1, batchSize);
    this.maxRetries = Math.max(1, maxRetries);
    this.metrics = metrics == null ? WorkflowMetrics.NOOP : metrics;
  }

  /** 跑一轮：取一批到期意图，按序执行。返回本轮成功落地条数。 */
  public int runOnce(int limit) {
    List<StatusIntent> due = outbox.pollDue(Math.max(1, limit));
    if (due.isEmpty()) {
      return 0;
    }
    long start = System.currentTimeMillis();
    // 本批次内已失败的实例：其后续意图必须延后，否则落地顺序被颠倒
    Set<String> blockedInstances = new HashSet<>();
    int done = 0;
    for (StatusIntent intent : due) {
      if (blockedInstances.contains(intent.instanceId())) {
        continue;
      }
      // 调度线程无 TenantContext：按行内 tenant_id 重建，执行/落标都在该租户上下文里进行
      Long previousTenant = TenantContext.getTenantIdAsLong();
      TenantContext.setTenantId(intent.tenantId());
      try {
        // 多副本原子抢占：只有 claim 成功的副本才执行，其余副本拿到 false 直接跳过——
        // 否则 Bone 多副本下两个副本同时 poll 同一条意图会重复执行 approve/reject 等业务动作。
        if (!outbox.claim(intent.id())) {
          continue;
        }
        executor.execute(intent);
        outbox.markDone(intent.id());
        done++;
        metrics.relayIntentExecuted();
      } catch (NonIdempotentActionRefusedException refused) {
        // P0-2 硬约束命中：动作未声明幂等，重投会双推进——拒绝执行并落终态 FAILED 转人工。
        // 不走退避重试（重试正是危险源）。
        outbox.markTerminallyFailed(intent.id(), refused.getMessage());
        blockedInstances.add(intent.instanceId());
        metrics.relayIntentTerminallyFailed();
        log.error(
            "[workflow] 状态投影因动作未声明幂等被拒绝，已落终态 FAILED 待人工接入幂等：intent={} instance={} action={}",
            intent.id(),
            intent.instanceId(),
            intent.actionCode());
      } catch (RuntimeException ex) {
        boolean rescheduled =
            outbox.markFailed(intent.id(), intent.retryCount(), maxRetries, backoffOf(intent));
        blockedInstances.add(intent.instanceId());
        metrics.relayIntentFailed();
        if (rescheduled) {
          log.warn(
              "[workflow] 状态投影失败，将按退避重试：intent={} instance={} attempt={}",
              intent.id(),
              intent.instanceId(),
              intent.retryCount() + 1,
              ex);
        } else {
          log.error(
              "[workflow] 状态投影重试已达上限，留在 FAILED 待人工介入：intent={} instance={}",
              intent.id(),
              intent.instanceId(),
              ex);
        }
      } finally {
        if (previousTenant == null) {
          TenantContext.clear();
        } else {
          TenantContext.setTenantId(previousTenant);
        }
      }
    }
    metrics.relayRound(System.currentTimeMillis() - start);
    return done;
  }

  @Scheduled(
      fixedDelayString = "${bone.workflow.status-relay.fixed-delay:2000}",
      initialDelayString = "${bone.workflow.status-relay.initial-delay:5000}")
  public void runScheduled() {
    try {
      runOnce(batchSize);
    } catch (RuntimeException ex) {
      // 中继自身异常不得冒泡到调度器（否则后续周期可能被跳过）；下一轮自然重试
      log.error("[workflow] 状态投影中继本轮异常（下轮重试）", ex);
    }
  }

  /**
   * 对账补投：把「phase 已终态、但意图行根本没落库」的实例补写成意图，交由下一轮中继落地。
   *
   * <p><b>补投 id 是确定性的</b>（{@code reconcile-{instanceId}-{phase}}）：重复对账同一实例时第二次插入撞主键，
   * 按「已在途」跳过——否则每轮对账都会重复补投，把有上限的重试变成无限重试。动作码由行内 phase 推导（与写入侧一致），
   * 无对应业务动作的终态（TERMINATED）补投后由中继按「无动作意图」直接标记 DONE，闭合证据链。
   *
   * <p>返回本轮补投条数。跨租户：逐条按行内 {@code tenant_id} 重建上下文后插入（{@code insert} 要租户上下文）。
   */
  public int runReconcile(int limit) {
    List<StatusIntent> missing =
        outbox.findTerminalMissingProjection(
            Math.max(1, limit), WorkflowStatusOutboxRepository.RECONCILE_WINDOW);
    int inserted = 0;
    for (StatusIntent gap : missing) {
      Long previousTenant = TenantContext.getTenantIdAsLong();
      TenantContext.setTenantId(gap.tenantId());
      try {
        outbox.insert(
            new StatusIntent(
                "reconcile-" + gap.instanceId() + "-" + gap.intent(),
                gap.instanceId(),
                gap.entityCode(),
                gap.recordId(),
                gap.tenantId(),
                null, // 对账线程只看到终态，拿不到决策人；由执行器落成系统身份，不冒用发起人
                gap.intent(),
                gap.actionCode(),
                null,
                "NEW",
                0,
                null));
        inserted++;
        log.warn(
            "[workflow] 对账补投状态投影意图（疑似崩溃在 phase CAS 与写意图之间）：instance={} intent={}",
            gap.instanceId(),
            gap.intent());
      } catch (DuplicateKeyException alreadyInFlight) {
        // 已在途（上一轮对账已补投 / 写入侧其实已落库）：跳过，不得重复补投
      } finally {
        if (previousTenant == null) {
          TenantContext.clear();
        } else {
          TenantContext.setTenantId(previousTenant);
        }
      }
    }
    return inserted;
  }

  @Scheduled(
      fixedDelayString = "${bone.workflow.status-reconcile.fixed-delay:30000}",
      initialDelayString = "${bone.workflow.status-reconcile.initial-delay:15000}")
  public void runReconcileScheduled() {
    try {
      runReconcile(batchSize);
    } catch (RuntimeException ex) {
      log.error("[workflow] 状态投影对账本轮异常（下轮重试）", ex);
    }
  }

  /** 按保留期物理清理 DONE 行，返回清理条数。保留期必须 &gt; 对账窗口（见 {@code WorkflowStatusOutboxRepository}）。 */
  public int purgeDone() {
    return outbox.purgeDone(WorkflowStatusOutboxRepository.DONE_RETENTION);
  }

  @Scheduled(cron = "${bone.workflow.status-purge-cron:0 30 3 * * ?}")
  public void purgeScheduled() {
    try {
      int purged = purgeDone();
      if (purged > 0) {
        log.info("[workflow] 状态投影发件箱清理 DONE 行：{} 条", purged);
      }
    } catch (RuntimeException ex) {
      log.error("[workflow] 状态投影发件箱清理本轮异常（下轮重试）", ex);
    }
  }

  /** 指数退避：1s → 2s → 4s … 封顶 5 分钟。 */
  private Instant backoffOf(StatusIntent intent) {
    long shift = Math.min(intent.retryCount(), 20);
    Duration backoff = BASE_BACKOFF.multipliedBy(1L << shift);
    if (backoff.compareTo(MAX_BACKOFF) > 0) {
      backoff = MAX_BACKOFF;
    }
    return Instant.now().plus(backoff);
  }
}
