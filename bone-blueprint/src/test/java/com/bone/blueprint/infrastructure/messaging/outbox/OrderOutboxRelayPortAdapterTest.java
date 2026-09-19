package com.bone.blueprint.infrastructure.messaging.outbox;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.bone.blueprint.application.port.out.OrderMessagePort;
import com.bone.blueprint.infrastructure.config.OrderOutboxProperties;
import com.bone.core.tenant.context.TenantContext;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class OrderOutboxRelayTest {

  private static final String TOPIC = "domain.order.order_paid.v1";

  private static final String DEAD_LETTER_TOPIC = "platform.dead_letter.v1";

  @Mock private OrderOutboxProperties properties;

  @Mock private OrderOutboxRepository outboxRepository;

  @Mock private OrderMessagePort messageSender;

  /** 真实注册表（非 mock）：{@code counter(...)} 需要返回可 increment 的对象。 */
  private final MeterRegistry meterRegistry = new SimpleMeterRegistry();

  private OrderOutboxRelayPortAdapter relay;

  private OrderOutboxRecord record;

  @BeforeEach
  void setUp() {
    relay =
        new OrderOutboxRelayPortAdapter(properties, outboxRepository, messageSender, meterRegistry);
    when(properties.isEnabled()).thenReturn(true);
    when(properties.getBatchSize()).thenReturn(10);
    when(properties.getMaxRetries()).thenReturn(3);
    when(properties.getDeadLetterTopic()).thenReturn(DEAD_LETTER_TOPIC);
    record =
        OrderOutboxRecord.pending(
            1L, 100L, "evt-1", "OrderPaidIntegrationEvent", TOPIC, "100", "{\"orderId\":1}");
  }

  @AfterEach
  void tearDown() {
    TenantContext.clear();
  }

  @Test
  void relayPendingMarksSentOnSuccess() {
    when(outboxRepository.findByCriteria(any())).thenReturn(List.of(record));

    int sent = relay.relayPending();

    assertEquals(1, sent);
    verify(messageSender, times(1)).send(TOPIC, "100", record.getEnvelopeJson());
    verify(outboxRepository, times(1)).update(record);
    assertEquals(OutboxStatus.SENT, record.getStatus());
  }

  @Test
  void relayPendingRetriesBeforeFailed() {
    when(outboxRepository.findByCriteria(any())).thenReturn(List.of(record));
    doThrow(new RuntimeException("mq down")).when(messageSender).send(any(), any(), any());

    int sent = relay.relayPending();

    assertEquals(0, sent);
    assertEquals(OutboxStatus.PENDING, record.getStatus());
    assertEquals(1, record.getRetryCount());
    verify(outboxRepository, times(1)).update(record);
    // 未到上限：不转死信（只投递了一次原主题，且已失败），留在 Outbox 等下次重试
    verify(messageSender, times(1)).send(any(), any(), any());
  }

  @Test
  void relayPendingMarksFailedAndForwardsToDeadLetterAfterMaxRetries() {
    record.incrementRetry();
    record.incrementRetry();
    when(outboxRepository.findByCriteria(any())).thenReturn(List.of(record));
    // 仅原主题投递失败，死信通道正常
    doThrow(new RuntimeException("mq down"))
        .when(messageSender)
        .send(TOPIC, record.getPartitionKey(), record.getEnvelopeJson());

    relay.relayPending();

    assertEquals(OutboxStatus.FAILED, record.getStatus());
    // 终端失败必须转投死信，否则事实只能在表里躺着而无人知晓（消息与事件规范 §6）
    verify(messageSender, times(1))
        .send(DEAD_LETTER_TOPIC, record.getPartitionKey(), record.getEnvelopeJson());
  }

  /**
   * 中继线程没有请求上下文，而写回 bp_outbox 是租户表操作：必须逐条以「该记录的租户」执行，否则被 ADR-0029 失败关闭拦下 （整轮中继回滚、每 5
   * 秒重复失败）。同时锁住「跑完不残留上下文」——线程池复用时不得串租户。
   */
  @Test
  void relayOneRunsInRecordTenantContextAndLeavesNoResidue() {
    when(outboxRepository.findByCriteria(any())).thenReturn(List.of(record));
    AtomicReference<String> tenantDuringWrite = new AtomicReference<>();
    doAnswer(
            invocation -> {
              tenantDuringWrite.set(TenantContext.getTenantId());
              return true;
            })
        .when(outboxRepository)
        .update(record);

    relay.relayPending();

    assertEquals("100", tenantDuringWrite.get());
    assertNull(TenantContext.getTenantId());
  }

  /** 一批里含多个租户的记录：每条都必须在自己租户内写回（挡住"整轮只用首条租户包裹"这类回退）。 */
  @Test
  void eachRecordIsRelayedUnderItsOwnTenant() {
    OrderOutboxRecord other =
        OrderOutboxRecord.pending(
            2L, 200L, "evt-2", "OrderPaidIntegrationEvent", TOPIC, "200", "{\"orderId\":2}");
    when(outboxRepository.findByCriteria(any())).thenReturn(List.of(record, other));
    Map<String, String> tenantByEventId = new HashMap<>();
    doAnswer(
            invocation -> {
              OrderOutboxRecord written = invocation.getArgument(0);
              tenantByEventId.put(written.getEventId(), TenantContext.getTenantId());
              return true;
            })
        .when(outboxRepository)
        .update(any());

    relay.relayPending();

    assertEquals("100", tenantByEventId.get("evt-1"));
    assertEquals("200", tenantByEventId.get("evt-2"));
    assertNull(TenantContext.getTenantId());
  }

  /** 写回失败（异常穿出 relayPending）时也必须恢复上下文，否则中继线程会带着残留租户继续跑下一批。 */
  @Test
  void contextIsRestoredWhenWriteFails() {
    when(outboxRepository.findByCriteria(any())).thenReturn(List.of(record));
    doThrow(new IllegalStateException("db down")).when(outboxRepository).update(any());

    assertThrows(IllegalStateException.class, () -> relay.relayPending());

    assertNull(TenantContext.getTenantId());
  }

  @Test
  void relayPendingSkipsWhenDisabled() {
    when(properties.isEnabled()).thenReturn(false);

    int sent = relay.relayPending();

    assertEquals(0, sent);
    verify(outboxRepository, never()).findByCriteria(any());
  }
}
