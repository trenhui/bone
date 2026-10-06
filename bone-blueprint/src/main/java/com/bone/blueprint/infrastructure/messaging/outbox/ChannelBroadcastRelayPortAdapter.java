package com.bone.blueprint.infrastructure.messaging.outbox;

import com.bone.blueprint.application.port.out.ChannelBroadcastRelayPort;
import com.bone.blueprint.application.port.out.ChannelExtensionPort;
import com.bone.blueprint.domain.extension.channel.ChannelListingResult;
import com.bone.blueprint.domain.extension.channel.ChannelProductContext;
import com.bone.blueprint.domain.model.channel.ChannelProduct;
import com.bone.blueprint.domain.repository.ChannelProductRepository;
import com.bone.blueprint.infrastructure.config.ChannelBroadcastProperties;
import com.bone.core.tenant.context.TenantContextRunner;
import com.bone.metadata.sdk.query.criteria.Criteria;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 渠道库存广播中继 —— CAS 抢占 + 合并同商品旧任务 + 指数退避 + 死信 + 人工重试入口。
 *
 * <p><b>为什么不能沿用「库存事务内同步循环」</b>：那样渠道 HTTP 超时会拖住库存事务的行锁与连接， 且「A 渠道已改、B 渠道超时」造成的两侧不一致
 * 没有任何自愈手段。中继把「投递」从业务事务里摘出来后，失败可重试、卡死可自愈、耗尽可人工介入。
 *
 * <p><b>同商品同渠道只投最新库存（合并）</b>：一次促销可能在一分钟内把库存 100→80→60，若逐条投递，渠道会被同步 3 次， 而渠道只需要最终值。 因此抢占后按 {@code
 * (productId, channelCode)} 分组，只投 {@code createdAt} 最大的一条， 其余标 SENT 并记 {@code mergedIntoId}。
 * 这不是「丢消息」——被合并的旧库存值本来就不该出现在渠道。
 *
 * <p><b>三条硬规则</b>：
 *
 * <ol>
 *   <li><b>网络 IO 绝不在 DB 事务内</b>：抢占与终态标记都是极短小事务，调渠道在事务外；
 *   <li><b>抢占用 CAS 而非悲观锁</b>：{@code updateByCriteria} 带 {@code WHERE status='PENDING'}，
 *       多实例并发各抢互不重叠的行集， 零死锁 （HC-0031 禁 FOR UPDATE）；
 *   <li><b>终态标记独立事务</b>：{@code TransactionTemplate(REQUIRES_NEW)}， 避免被未来可能加上的外层事务吞掉。
 * </ol>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ChannelBroadcastRelayPortAdapter implements ChannelBroadcastRelayPort {

  private final ChannelBroadcastProperties properties;
  private final ChannelBroadcastTaskRepository broadcastTaskRepository;
  private final ChannelProductRepository channelProductRepository;
  private final ChannelExtensionPort channelExtensionPort;
  private final TransactionTemplate txTemplate;

  /** 强制 REQUIRES_NEW：标记终态必须是独立小事务（与 {@code OrderOutboxRelayPortAdapter} 同理）。 */
  @jakarta.annotation.PostConstruct
  void init() {
    txTemplate.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    txTemplate.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);
  }

  @Override
  public int relayPending() {
    if (!properties.isEnabled()) {
      return 0;
    }
    // 阶段 0：自愈卡死的 PROCESSING（上次中继崩溃遗留）。必须在抢占新批次之前，
    // 否则本轮刚抢的 PROCESSING 会被误判为卡死并被回退，造成重复投递。
    reconcileStuck();

    // 阶段 1：CAS 抢占 PENDING → PROCESSING（极短事务）
    if (claimBatch() == 0) {
      return 0;
    }

    // 阶段 2：读回 PROCESSING
    List<ChannelBroadcastTask> claimed = broadcastTaskRepository.findProcessing();
    if (claimed == null || claimed.isEmpty()) {
      return 0;
    }

    // 阶段 3：合并同商品同渠道，只保留最新库存值
    List<ChannelBroadcastTask> toDeliver = mergeDuplicates(claimed);

    // 阶段 4：逐条投递（网络 IO，事务外）
    int sent = 0;
    for (ChannelBroadcastTask task : toDeliver) {
      if (TenantContextRunner.callAs(task.getTenantId(), () -> relayOne(task))) {
        sent++;
      }
    }
    log.info(
        "渠道库存广播本轮完成 | 抢占={} | 合并={} | 投递={} | 成功={}",
        claimed.size(),
        claimed.size() - toDeliver.size(),
        toDeliver.size(),
        sent);
    return sent;
  }

  /**
   * 同 {@code (productId, channelCode)} 只保留 {@code createdAt} 最大的一条，其余标 SENT 并记合并去向。
   *
   * <p>用 {@code createdAt} 而非 {@code updatedAt} 判断新旧：入队时间与「库存被改成这个值」的时间同源，而 {@code updatedAt}
   * 会被抢占/退避动作刷新， 拿它排序会在重试场景下判错新旧。
   */
  private List<ChannelBroadcastTask> mergeDuplicates(List<ChannelBroadcastTask> claimed) {
    Map<String, List<ChannelBroadcastTask>> groups = new LinkedHashMap<>();
    for (ChannelBroadcastTask task : claimed) {
      String key = task.getProductId() + "@" + task.getChannelCode();
      groups.computeIfAbsent(key, k -> new ArrayList<>()).add(task);
    }
    List<ChannelBroadcastTask> keep = new ArrayList<>();
    for (List<ChannelBroadcastTask> group : groups.values()) {
      if (group.size() == 1) {
        keep.add(group.get(0));
        continue;
      }
      ChannelBroadcastTask newest = group.get(0);
      for (ChannelBroadcastTask candidate : group) {
        if (isNewer(candidate, newest)) {
          newest = candidate;
        }
      }
      for (ChannelBroadcastTask task : group) {
        if (task == newest) {
          continue;
        }
        task.markMergedInto(newest.getId());
        if (markTerminal(task)) {
          log.info(
              "渠道库存广播合并 | 旧任务={} 合并到新任务={} | product={} | channel={} | 旧库存={} → 新库存={}",
              task.getId(),
              newest.getId(),
              task.getProductId(),
              task.getChannelCode(),
              task.getTargetStock(),
              newest.getTargetStock());
        }
      }
      keep.add(newest);
    }
    return keep;
  }

  private static boolean isNewer(ChannelBroadcastTask candidate, ChannelBroadcastTask current) {
    if (candidate.getCreatedAt() == null) {
      return false;
    }
    if (current.getCreatedAt() == null) {
      return true;
    }
    return candidate.getCreatedAt().isAfter(current.getCreatedAt());
  }

  /** CAS 抢占：{@code updateByCriteria} 带 {@code WHERE id=? AND status='PENDING'}。 */
  private int claimBatch() {
    List<ChannelBroadcastTask> pendings =
        broadcastTaskRepository.findPendingBatch(properties.getBatchSize());
    if (pendings == null || pendings.isEmpty()) {
      return 0;
    }
    int claimed = 0;
    for (ChannelBroadcastTask task : pendings) {
      task.markClaimed();
      int n =
          broadcastTaskRepository.updateByCriteria(
              task,
              Criteria.<ChannelBroadcastTask>create()
                  .eq(ChannelBroadcastTask::getId, task.getId())
                  .eq(ChannelBroadcastTask::getStatus, BroadcastTaskStatus.PENDING)
                  .disableTenantFilter());
      if (n > 0) {
        claimed++;
      }
    }
    return claimed;
  }

  /** 自愈卡死的 PROCESSING（更新时刻早于 stuckTimeoutMs 视为上次中继崩溃遗留）。 */
  private void reconcileStuck() {
    try {
      Instant threshold = Instant.now().minusMillis(properties.getStuckTimeoutMs());
      List<ChannelBroadcastTask> stuck = broadcastTaskRepository.findStuckProcessing(threshold);
      if (stuck == null || stuck.isEmpty()) {
        return;
      }
      int healed = 0;
      for (ChannelBroadcastTask task : stuck) {
        int n =
            broadcastTaskRepository.updateByCriteria(
                task,
                Criteria.<ChannelBroadcastTask>create()
                    .eq(ChannelBroadcastTask::getId, task.getId())
                    .eq(ChannelBroadcastTask::getStatus, BroadcastTaskStatus.PROCESSING)
                    .disableTenantFilter());
        if (n > 0) {
          healed++;
          log.warn("渠道库存广播自愈卡死 PROCESSING → PENDING | taskId={}", task.getId());
        }
      }
      if (healed > 0) {
        log.info("渠道库存广播本轮自愈卡死任务: {} 条", healed);
      }
    } catch (DataAccessException ex) {
      log.warn("渠道库存广播自愈 PROCESSING 失败（忽略，下次重试）: {}", ex.getMessage());
    }
  }

  /** 单条投递：调渠道扩展点同步库存 → 标记终态 + 回写渠道商品库存快照。 */
  private boolean relayOne(ChannelBroadcastTask task) {
    if (task.getChannelProductId() == null || task.getChannelProductId().isBlank()) {
      // 没上架成功过就没有渠道商品ID，投递必然失败；直接死信并写明原因，避免无意义的重试风暴。
      task.markFailed("渠道商品ID为空（该渠道尚未上架成功，无法同步库存）", null);
      markTerminal(task);
      log.warn("渠道库存广播终止 | taskId={} | 原因=渠道商品ID为空", task.getId());
      return false;
    }
    try {
      ChannelListingResult result =
          channelExtensionPort.syncInventory(
              ChannelProductContext.forExisting(
                  task.getTenantId(),
                  task.getChannelCode(),
                  task.getProductId(),
                  task.getProductName(),
                  task.getChannelProductId(),
                  task.getTargetStock()));
      if (result.success()) {
        task.markSent();
        markTerminal(task);
        syncChannelProductSnapshot(task);
        log.info(
            "渠道库存广播成功 | taskId={} | product={} | channel={} | stock={}",
            task.getId(),
            task.getProductId(),
            task.getChannelCode(),
            task.getTargetStock());
        return true;
      }
      // 渠道明确拒绝（资质/类目/限流）：重试有意义（限流尤其），走退避重试；耗尽后死信。
      task.markFailed(
          result.errorCode() + " - " + result.message(), nextRetryAt(task.getRetryCount()));
      markTerminal(task);
      log.warn(
          "渠道库存广播被拒 | taskId={} | channel={} | code={} | retry={}",
          task.getId(),
          task.getChannelCode(),
          result.errorCode(),
          task.getRetryCount());
      return false;
    } catch (RuntimeException ex) {
      // 扩展点抛错 = 凭证缺失 / 网络故障 / 渠道 5xx：同样退避重试，不吞异常。
      task.markFailed("投递异常: " + ex.getMessage(), nextRetryAt(task.getRetryCount()));
      markTerminal(task);
      log.error("渠道库存广播异常 | taskId={} | channel={}", task.getId(), task.getChannelCode(), ex);
      return false;
    }
  }

  /** 指数退避：{@code base * 2^(retry-1)}，封顶 {@code maxBackoffMs}。 */
  private Instant nextRetryAt(Integer retriedTimes) {
    int n = retriedTimes == null || retriedTimes < 1 ? 1 : retriedTimes;
    long delay = properties.getBaseBackoffMs();
    for (int i = 1; i < n && delay < properties.getMaxBackoffMs(); i++) {
      delay = delay * 2;
    }
    return Instant.now().plusMillis(Math.min(delay, properties.getMaxBackoffMs()));
  }

  /** CAS 标记终态（{@code WHERE status='PROCESSING'}），独立小事务。 */
  private boolean markTerminal(ChannelBroadcastTask task) {
    Integer updated =
        txTemplate.execute(
            status ->
                broadcastTaskRepository.updateByCriteria(
                    task,
                    Criteria.<ChannelBroadcastTask>create()
                        .eq(ChannelBroadcastTask::getId, task.getId())
                        .eq(ChannelBroadcastTask::getStatus, BroadcastTaskStatus.PROCESSING)
                        .disableTenantFilter()));
    return updated != null && updated > 0;
  }

  /**
   * 回写渠道商品的库存快照与状态。
   *
   * <p>不在中继里 markFailed 渠道商品：广播失败是<strong>投递问题</strong>（任务表已记录 FAILED + 原因 + 可人工重试）， 把渠道商品置 FAILED
   * 会让「上架状态」被一次限流污染成失败态，运营反而看不出商品其实在线。 渠道商品只在真正下架/上架失败时改状态。
   */
  private void syncChannelProductSnapshot(ChannelBroadcastTask task) {
    try {
      txTemplate.executeWithoutResult(
          status -> {
            ChannelProduct entity =
                channelProductRepository.findOne(
                    task.getTenantId(), task.getChannelCode(), task.getProductId());
            if (entity == null) {
              return;
            }
            entity.markStockSynced(task.getTargetStock(), Instant.now());
            channelProductRepository.update(entity);
          });
    } catch (RuntimeException ex) {
      // 快照失败不影响「渠道已同步」这个事实（渠道侧库存已改），只丢本地可观测字段。
      log.warn("渠道商品库存快照回写失败（渠道侧已同步）| taskId={}", task.getId(), ex);
    }
  }
}
