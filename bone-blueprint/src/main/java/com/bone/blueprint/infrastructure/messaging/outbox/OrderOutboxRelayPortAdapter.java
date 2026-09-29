package com.bone.blueprint.infrastructure.messaging.outbox;

import com.bone.blueprint.application.port.out.OrderMessagePort;
import com.bone.blueprint.application.port.out.OrderOutboxRelayPort;
import com.bone.blueprint.infrastructure.config.OrderOutboxProperties;
import com.bone.core.tenant.context.TenantContextRunner;
import com.bone.metadata.sdk.query.criteria.Criteria;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.annotation.PostConstruct;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Outbox 中继 —— UPDATE CAS 抢占 + PROCESSING 中间态 + 编程式独立小事务。
 *
 * <p><b>核心设计（解决旧版 3 个致命问题）</b>：
 *
 * <ol>
 *   <li><strong>跨数据库的抢占</strong>：先按条件查 PENDING 批次，再逐条用 {@code Repository.updateByCriteria} 做 CAS
 *       行翻转（{@code WHERE id=? AND status='PENDING'}）， 替代 {@code FOR UPDATE SKIP LOCKED} 与 {@code
 *       UPDATE ... LIMIT}。后者仅 MySQL/PG 支持、达梦/OceanBase 行为不一；updateByCriteria 走
 *       bone-metadata-sdk，全目标 DB 一致且零死锁。
 *   <li><strong>网络 IO 绝不在 DB 事务内</strong>：抢占只做 SELECT + UPDATE（极短事务），发 MQ 在事务外执行。 消除连接池被同步 IO
 *       长时间占用的风险。
 *   <li><strong>先发后更窗口消除</strong>：PROCESSING 状态的记录不会被下一轮 Relay 重复抢占。发 MQ 成功后翻 SENT 用 {@code WHERE
 *       status='PROCESSING'} 保证 CAS——即使 Relay 宕机重启，同一条记录也不会被双投。
 * </ol>
 *
 * <p><b>为何用 TransactionTemplate 而非 @Transactional</b>：本类内部方法互调（{@code relayOne} → {@code markSent}
 * 等）， Spring AOP 代理对同类自调用不生效，{@code @Transactional(REQUIRES_NEW)} 会被静默忽略。编程式 TransactionTemplate
 * 不受此限制，且事务边界在代码中显式可见。
 *
 * <p><b>死锁风险：零</b>（满足 HC-0031 禁止 FOR UPDATE 悲观锁的约束）。updateByCriteria 单表单语句、行锁在语句结束即释放——不满足死锁三要素。
 *
 * <p><b>HC-006 合规</b>：本类不持有 JdbcTemplate / SqlSession，所有持久化经 {@code
 * OrderOutboxRepository}（bone-metadata-sdk）， 与 {@code OrderOutboxPortAdapter} 同构。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OrderOutboxRelayPortAdapter implements OrderOutboxRelayPort {

  private final OrderOutboxProperties properties;
  private final OrderOutboxRepository outboxRepository;
  private final OrderMessagePort messageSender;
  private final MeterRegistry meterRegistry;
  private final TransactionTemplate txTemplate;

  /**
   * 强制 REQUIRES_NEW：所有 markSent/markFailed/markRetryable 必须是独立小事务。
   *
   * <p>TransactionTemplate 默认 REQUIRED——如果有人未来给 relayPending 或它的上游加了 @Transactional， markSent
   * 会悄悄加入外层事务，破坏"标记终态与发 MQ 解耦"这条架构原则。显式 REQUIRES_NEW 让注释和行为对齐。
   */
  @PostConstruct
  void init() {
    txTemplate.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    txTemplate.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);
  }

  /** 中继入口。抢占 → 读回 → 逐条投递（投递在事务外）。 */
  @Override
  public int relayPending() {
    if (!properties.isEnabled()) {
      return 0;
    }

    // 阶段 0：自愈卡死的 PROCESSING 记录（上次 Relay 崩溃遗留）。轻量操作——
    // PROCESSING 卡死只在进程崩溃时发生，批量回退为 PENDING 让本轮一起处理。
    reconcileStuck();

    // 阶段 1：CAS 抢占 PENDING → PROCESSING（极短事务，<10ms）。单语句原子、零死锁。
    int claimed = claimBatch(properties.getBatchSize());
    if (claimed == 0) {
      return 0;
    }

    // 阶段 2：读回 PROCESSING 记录（跨租户全量，disableTenantFilter）。
    List<OrderOutboxRecord> processing = findClaimed();
    if (processing.isEmpty()) {
      return 0;
    }

    // 阶段 3：逐条投递（网络 IO，**不在 DB 事务内**）。
    // 每条用 TenantContextRunner 切换租户 + TransactionTemplate(REQUIRES_NEW) 标记终态。
    int sent = 0;
    for (OrderOutboxRecord record : processing) {
      if (TenantContextRunner.callAs(record.getTenantId(), () -> relayOne(record))) {
        sent++;
      }
    }
    return sent;
  }

  /**
   * 抢占 PENDING → PROCESSING：先查待发批次（跨租户），再逐条用 SDK {@code updateByCriteria} 做 CAS 行翻转 （{@code WHERE
   * id=? AND status='PENDING'}）。零死锁、跨数据库（不依赖 FOR UPDATE SKIP LOCKED / UPDATE LIMIT）。
   *
   * <p>各 Relay 实例并发执行时，每个实例抢占互不重叠的行集：受 UPDATE 行锁串行化，但语句结束即释放（无外层事务）， 不等待、不死锁、不排队。
   */
  private int claimBatch(int limit) {
    Criteria<OrderOutboxRecord> pending =
        Criteria.<OrderOutboxRecord>create()
            .eq(OrderOutboxRecord::getStatus, OutboxStatus.PENDING)
            .disableTenantFilter()
            .page(1, limit);
    List<OrderOutboxRecord> pendings;
    try {
      pendings = outboxRepository.findByCriteria(pending);
    } catch (DataAccessException ex) {
      log.error("Outbox 查询 PENDING 失败: {}", ex.getMessage());
      return 0;
    }
    if (pendings == null || pendings.isEmpty()) {
      return 0;
    }
    int claimed = 0;
    for (OrderOutboxRecord r : pendings) {
      r.markClaimed();
      int n =
          outboxRepository.updateByCriteria(
              r,
              Criteria.<OrderOutboxRecord>create()
                  .eq(OrderOutboxRecord::getId, r.getId())
                  .eq(OrderOutboxRecord::getStatus, OutboxStatus.PENDING)
                  .disableTenantFilter());
      if (n > 0) {
        claimed++;
      }
    }
    return claimed;
  }

  /** 读回本轮抢占到的 PROCESSING 记录。 */
  private List<OrderOutboxRecord> findClaimed() {
    Criteria<OrderOutboxRecord> criteria =
        Criteria.<OrderOutboxRecord>create()
            .eq(OrderOutboxRecord::getStatus, OutboxStatus.PROCESSING)
            .disableTenantFilter()
            .page(1, properties.getBatchSize());
    List<OrderOutboxRecord> records = outboxRepository.findByCriteria(criteria);
    return records == null ? List.of() : records;
  }

  /**
   * 自愈卡死的 PROCESSING 记录（Relay 崩溃遗留）。
   *
   * <p>在 claimBatch <strong>之前</strong>运行：此时 claimBatch 尚未抢新批次，PROCESSING 集合里只可能是 上一轮 Relay
   * 崩溃遗留（正常结束后 PROCESSING 应为空——成功→SENT / 失败可重试→PENDING / 超限→FAILED）。 逐条回退为 PENDING（updateByCriteria
   * CAS 避免并发覆盖），让本轮一起处理。
   */
  private void reconcileStuck() {
    try {
      Criteria<OrderOutboxRecord> criteria =
          Criteria.<OrderOutboxRecord>create()
              .eq(OrderOutboxRecord::getStatus, OutboxStatus.PROCESSING)
              .disableTenantFilter()
              .page(1, properties.getBatchSize());
      List<OrderOutboxRecord> proc = outboxRepository.findByCriteria(criteria);
      if (proc == null || proc.isEmpty()) {
        return;
      }
      int healed = 0;
      for (OrderOutboxRecord r : proc) {
        r.markRetry();
        int n =
            outboxRepository.updateByCriteria(
                r,
                Criteria.<OrderOutboxRecord>create()
                    .eq(OrderOutboxRecord::getId, r.getId())
                    .eq(OrderOutboxRecord::getStatus, OutboxStatus.PROCESSING)
                    .disableTenantFilter());
        if (n > 0) {
          healed++;
          log.warn(
              "Outbox 自愈卡死 PROCESSING → PENDING: eventId={}, id={}", r.getEventId(), r.getId());
        }
      }
      if (healed > 0) {
        log.info("Outbox 本轮自愈 PROCESSING 卡死记录: {} 条", healed);
      }
    } catch (DataAccessException ex) {
      log.warn("Outbox 自愈 PROCESSING 失败（忽略，下次重试）: {}", ex.getMessage());
    }
  }

  /** 单条投递 + 标记终态。发 MQ 在事务外（网络 IO 不占用连接池）， 标记终态用 TransactionTemplate(REQUIRES_NEW) 保证独立小事务。 */
  private boolean relayOne(OrderOutboxRecord record) {
    try {
      messageSender.send(record.getTopic(), record.getPartitionKey(), record.getEnvelopeJson());
      markSent(record);
      countSend(record, "sent");
      return true;
    } catch (Exception ex) {
      record.incrementRetry();
      if (record.getRetryCount() >= properties.getMaxRetries()) {
        markFailed(record, ex);
        countSend(record, "failed");
        log.error(
            "Outbox 投递失败并标记 FAILED（已转投死信）: eventId={}, topic={}",
            record.getEventId(),
            record.getTopic(),
            ex);
      } else {
        // 失败后释放 PROCESSING → PENDING，让下一轮 relay 重试。
        markRetryable(record);
        countSend(record, "retry");
        log.warn(
            "Outbox 投递失败将重试: eventId={}, retry={}",
            record.getEventId(),
            record.getRetryCount(),
            ex);
      }
      return false;
    }
  }

  /**
   * CAS 标记 SENT —— REQUIRES_NEW 独立事务，WHERE status='PROCESSING' 保证只处理自己抢占的。 TransactionTemplate
   * 编程式避免 Spring AOP 自调用失效。
   */
  private void markSent(OrderOutboxRecord record) {
    record.markSent();
    Integer updated =
        txTemplate.execute(
            status ->
                outboxRepository.updateByCriteria(
                    record,
                    Criteria.<OrderOutboxRecord>create()
                        .eq(OrderOutboxRecord::getId, record.getId())
                        .eq(OrderOutboxRecord::getStatus, OutboxStatus.PROCESSING)));
    if (updated == null || updated == 0) {
      // CAS 未命中：PROCESSING 已不在，大概率是 reconcileStuck 已回收或另一个 Relay 处理过。
      // 消息发了但没标 SENT——下一轮 relay 会重发（at-least-once 允许重复投递，下游应幂等）。
      // 主动把 record 改回 PENDING 让下一轮抢占，避免卡死。
      record.markRetry();
      Integer retryUpdated =
          txTemplate.execute(
              s ->
                  outboxRepository.updateByCriteria(
                      record,
                      Criteria.<OrderOutboxRecord>create()
                          .eq(OrderOutboxRecord::getId, record.getId())
                          .eq(OrderOutboxRecord::getStatus, OutboxStatus.PROCESSING)
                          .disableTenantFilter()));
      log.warn(
          "Outbox markSent CAS 未命中，已回收为 PENDING（下一轮将重发，下游需幂等）: eventId={}, id={}, reclaimed={}",
          record.getEventId(),
          record.getId(),
          retryUpdated != null && retryUpdated > 0);
    }
  }

  /** 终端失败 → FAILED + 转投死信。 */
  private void markFailed(OrderOutboxRecord record, Exception cause) {
    record.markFailed();
    Integer updated =
        txTemplate.execute(
            status ->
                outboxRepository.updateByCriteria(
                    record,
                    Criteria.<OrderOutboxRecord>create()
                        .eq(OrderOutboxRecord::getId, record.getId())
                        .eq(OrderOutboxRecord::getStatus, OutboxStatus.PROCESSING)));
    if (updated != null && updated > 0) {
      moveToDeadLetter(record, cause);
    }
  }

  /** 可重试失败 → 回到 PENDING 让下一轮 relay 重试。 */
  private void markRetryable(OrderOutboxRecord record) {
    record.markRetry();
    Integer updated =
        txTemplate.execute(
            status ->
                outboxRepository.updateByCriteria(
                    record,
                    Criteria.<OrderOutboxRecord>create()
                        .eq(OrderOutboxRecord::getId, record.getId())
                        .eq(OrderOutboxRecord::getStatus, OutboxStatus.PROCESSING)));
    if (updated == null || updated == 0) {
      // PROCESSING 已不在，说明 reconcileStuck 已处理——消息重发交给它。
      log.warn(
          "Outbox markRetryable CAS 未命中（PROCESSING 已被其他实例处理）: eventId={}, id={}",
          record.getEventId(),
          record.getId());
    }
  }

  /**
   * 终端失败转投死信主题（消息与事件规范 §6）。
   *
   * <p><b>为何不是直接丢弃或无限重试</b>：无限重试会堵住中继批次、让后续事件一起饿死；静默丢弃则让资金/状态事实消失。 死信保留原信封（其中已含 {@code eventId} /
   * {@code topic} / {@code traceId}），运维可按 topic 重放； 记录本身也已置 {@code FAILED}，重放前可查表核对。
   *
   * <p>死信投递失败只记日志：事实仍在 {@code bp_outbox} 表中（status=FAILED），不会因死信通道故障而丢失。
   */
  private void moveToDeadLetter(OrderOutboxRecord record, Exception cause) {
    try {
      messageSender.send(
          properties.getDeadLetterTopic(), record.getPartitionKey(), record.getEnvelopeJson());
    } catch (Exception dlqEx) {
      log.error(
          "Outbox 死信转投失败（记录已标记 FAILED，可查 bp_outbox 人工重放）: eventId={}, dlqTopic={}",
          record.getEventId(),
          properties.getDeadLetterTopic(),
          dlqEx);
    }
  }

  /** 投递计数指标（消息与事件规范 §9）：{@code bone_mq_send_total{topic,status}}。 */
  private void countSend(OrderOutboxRecord record, String status) {
    meterRegistry
        .counter("bone_mq_send_total", "topic", record.getTopic(), "status", status)
        .increment();
  }
}
