package com.bone.blueprint.infrastructure.messaging.outbox;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.bone.blueprint.application.port.out.OrderMessagePort;
import com.bone.blueprint.infrastructure.config.OrderOutboxProperties;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Outbox Relay 核心行为测试 —— 锁住 4 个架构不变量：
 *
 * <ol>
 *   <li>UPDATE CAS 抢占 PENDING → PROCESSING 原子行翻转（走 SDK updateByCriteria 的 WHERE
 *       status='PENDING'，跨数据库兼容）。
 *   <li>发 MQ 在事务外，标记终态（SENT/FAILED/PENDING）用独立小事务。
 *   <li>markSent/markRetryable/markFailed 的 WHERE status='PROCESSING' 保证只处理自己抢占的。
 *   <li>关闭/无抢占/无记录等边界条件快速返回。
 * </ol>
 *
 * <p>注：SQL 拼装正确性集成测试覆盖（h2 schema 验证）。本单元测试通过 messageSender + 返回值验证 Relay 控制流：成功 → SENT，重试 →
 * PENDING，超限 → FAILED+DLQ。
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class OrderOutboxRelayPortAdapterTest {

  private static final String TOPIC = "domain.order.order_paid.v1";
  private static final String DEAD_LETTER_TOPIC = "domain.order.deadletter.v1";

  @Mock private OrderOutboxProperties properties;
  @Mock private OrderOutboxRepository outboxRepository;
  @Mock private OrderMessagePort messageSender;
  @Mock private TransactionTemplate txTemplate;

  private final MeterRegistry meterRegistry = new SimpleMeterRegistry();
  private final OrderOutboxRecord record =
      OrderOutboxRecord.pending(
          1L, 100L, "evt-1", "OrderPaidIntegrationEvent", TOPIC, "100", "{\"orderId\":1}");

  private OrderOutboxRelayPortAdapter relay;

  @BeforeEach
  void setUp() {
    relay =
        new OrderOutboxRelayPortAdapter(
            properties, outboxRepository, messageSender, meterRegistry, txTemplate);
    when(properties.isEnabled()).thenReturn(true);
    when(properties.getBatchSize()).thenReturn(100);
    when(properties.getMaxRetries()).thenReturn(3);
    when(properties.getDeadLetterTopic()).thenReturn(DEAD_LETTER_TOPIC);
    // SDK updateByCriteria（CAS 标记）默认成功返回 1。
    when(outboxRepository.updateByCriteria(any(), any())).thenReturn(1);
    // TransactionTemplate.execute 必须实际执行 callback（否则 markSent 不会真 update）
    when(txTemplate.execute(any(TransactionCallback.class)))
        .thenAnswer(
            invocation -> invocation.<TransactionCallback<?>>getArgument(0).doInTransaction(null));
  }

  /** 成功路径：CAS 抢占 → 读回 → 发 MQ → 返回 sent=1。 */
  @Test
  void relayPendingSuccessFlow() {
    when(outboxRepository.findByCriteria(any())).thenReturn(List.of(record));

    int sent = relay.relayPending();

    assertEquals(1, sent);
    verify(messageSender, times(1)).send(TOPIC, "100", record.getEnvelopeJson());
  }

  /** 发送失败但未到重试上限：返回 sent=0，不转死信。 */
  @Test
  void relayPendingRetriesBeforeFailed() {
    when(outboxRepository.findByCriteria(any())).thenReturn(List.of(record));
    org.mockito.Mockito.doThrow(new RuntimeException("mq down"))
        .when(messageSender)
        .send(any(), any(), any());

    int sent = relay.relayPending();

    assertEquals(0, sent);
    // 只调一次原主题发送（第一次失败后 markRetryable → 让下一轮 relay 重试）
    verify(messageSender, times(1)).send(TOPIC, "100", record.getEnvelopeJson());
    verify(messageSender, never()).send(eq(DEAD_LETTER_TOPIC), any(), any());
  }

  /** 达到最大重试次数：返回 sent=0 + 转投死信。 */
  @Test
  void relayPendingMarksFailedAndForwardsToDeadLetterAfterMaxRetries() {
    // 模拟已有 2 次重试（加上本次就是第 3 次，到达 maxRetries=3）
    record.incrementRetry();
    record.incrementRetry();
    when(outboxRepository.findByCriteria(any())).thenReturn(List.of(record));
    org.mockito.Mockito.doThrow(new RuntimeException("mq down"))
        .when(messageSender)
        .send(any(), any(), any());

    relay.relayPending();

    // 终端失败必须转投死信（消息与事件规范 §6）
    verify(messageSender, times(1))
        .send(DEAD_LETTER_TOPIC, record.getPartitionKey(), record.getEnvelopeJson());
  }

  /** Outbox 关闭时直接返回，不碰 DB 和 MQ。 */
  @Test
  void relayPendingSkipsWhenDisabled() {
    when(properties.isEnabled()).thenReturn(false);

    int sent = relay.relayPending();

    assertEquals(0, sent);
    verify(outboxRepository, never()).findByCriteria(any());
    verify(outboxRepository, never()).updateByCriteria(any(), any());
    verify(messageSender, never()).send(any(), any(), any());
  }

  /** CAS 抢占返回 0 时（全已被其他实例抢走 / 无 PENDING）直接返回，不做后续动作。 */
  @Test
  void relayPendingReturnsZeroWhenNothingClaimed() {
    when(outboxRepository.findByCriteria(any())).thenReturn(List.of());

    int sent = relay.relayPending();

    assertEquals(0, sent);
    verify(outboxRepository, never()).updateByCriteria(any(), any());
    verify(messageSender, never()).send(any(), any(), any());
  }
}
