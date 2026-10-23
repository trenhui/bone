package com.bone.workflow.starter.outbox;

import com.bone.core.tenant.context.TenantContext;
import java.time.Duration;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;

/**
 * 状态投影中继：把发件箱里的意图真正落到业务单据状态上。
 *
 * <p><b>三条正确性纪律</b>：
 *
 * <ol>
 *   <li><b>同实例按序落地</b>：意图按 id 升序取。若某条失败，本批次内<b>跳过该实例的后续意图</b>—— 否则会先落地后写的 REJECTED 再落地先写的
 *       APPROVED，把终态投影颠倒。
 *   <li><b>失败指数退避、有上限</b>：重试间隔 1s→2s→4s…封顶 5 分钟；达到上限不再排期，留在 FAILED
 *       交由对账/人工。不能无限重试——一个永久失败的动作（如动作定义被删）会让中继永久空转。
 *   <li><b>不跨实例互堵</b>：一个实例卡住不影响其它实例的投影（本批次跳过的只是同一 instanceId）。
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

  public StatusProjectionRelay(
      WorkflowStatusOutboxRepository outbox,
      StatusIntentExecutor executor,
      int batchSize,
      int maxRetries) {
    this.outbox = outbox;
    this.executor = executor;
    this.batchSize = Math.max(1, batchSize);
    this.maxRetries = Math.max(1, maxRetries);
  }

  /** 跑一轮：取一批到期意图，按序执行。返回本轮成功落地条数。 */
  public int runOnce(int limit) {
    List<StatusIntent> due = outbox.pollDue(Math.max(1, limit));
    if (due.isEmpty()) {
      return 0;
    }
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
      } catch (RuntimeException ex) {
        boolean rescheduled =
            outbox.markFailed(intent.id(), intent.retryCount(), maxRetries, backoffOf(intent));
        blockedInstances.add(intent.instanceId());
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
