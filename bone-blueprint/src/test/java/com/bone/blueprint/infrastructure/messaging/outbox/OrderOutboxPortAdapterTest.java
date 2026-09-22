package com.bone.blueprint.infrastructure.messaging.outbox;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.bone.blueprint.application.integration.event.OrderStockActionFailedIntegrationEvent;
import com.bone.blueprint.application.port.out.TenantPort;
import com.bone.blueprint.domain.model.order.event.OrderPaidEvent;
import com.bone.blueprint.infrastructure.config.OrderOutboxProperties;
import java.math.BigDecimal;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

/**
 * Outbox 写入测试（{@code OrderOutboxPort} 的基础设施实现）。
 *
 * <p>锁住三件事：① 每次追加都落一条 <strong>PENDING</strong> 记录并带上正确 topic/分区键（分区键为租户，顺序敏感链路 才不会错序）；②
 * 租户优先取<strong>事件自带值</strong>（跨租户误写会污染别的租户数据）；③ 信封由 ACL 转换后的
 * <em>集成事件</em>构成，而不是领域事件对象（跨上下文契约不能泄漏领域模型）。
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class OrderOutboxPortAdapterTest {

  private static final String TOPIC = "domain.order.order_paid.v1";

  @Mock private OrderOutboxProperties properties;

  @Mock private OrderOutboxRepository outboxRepository;

  @Mock private OrderOutboxEnvelopeFactory envelopeFactory;

  @Mock private TenantPort tenantProvider;

  @InjectMocks private OrderOutboxPortAdapter writer;

  @Test
  void appendsPendingRecordWithTopicPartitionKeyAndEnvelope() {
    Instant occurredAt = Instant.parse("2026-09-16T02:00:00Z");
    OrderPaidEvent event = new OrderPaidEvent(1L, 7L, 3L, new BigDecimal("100.00"), occurredAt);
    when(properties.isEnabled()).thenReturn(true);
    when(properties.getOrderPaidTopic()).thenReturn(TOPIC);
    when(envelopeFactory.newEventId()).thenReturn("e-1");
    when(envelopeFactory.toJson(any(), any(), any(), anyLong(), any(), any()))
        .thenReturn("{\"eventId\":\"e-1\"}");

    writer.appendOrderPaid(event);

    ArgumentCaptor<OrderOutboxRecord> recordCaptor =
        ArgumentCaptor.forClass(OrderOutboxRecord.class);
    verify(outboxRepository).save(recordCaptor.capture());
    OrderOutboxRecord record = recordCaptor.getValue();
    assertEquals("e-1", record.getEventId());
    assertEquals(TOPIC, record.getTopic());
    assertEquals(OutboxStatus.PENDING, record.getStatus());
    assertEquals("7", record.getPartitionKey(), "分区键取租户，保证同一租户的事件顺序");
    assertEquals(0, record.getRetryCount());

    // 信封载荷必须是 ACL 转换后的集成事件，且事实时间来自领域事件（不是落库时刻）
    ArgumentCaptor<Object> payloadCaptor = ArgumentCaptor.forClass(Object.class);
    verify(envelopeFactory)
        .toJson(
            eq("e-1"),
            eq("OrderPaidIntegrationEvent"),
            eq(TOPIC),
            eq(7L),
            eq(occurredAt),
            payloadCaptor.capture());
    assertEquals(
        "OrderPaidIntegrationEvent",
        payloadCaptor.getValue().getClass().getSimpleName(),
        "信封载荷必须是 ACL 转换后的集成事件，而不是领域事件对象");
  }

  @Test
  void prefersTenantCarriedByEventOverThreadContext() {
    OrderPaidEvent event = new OrderPaidEvent(1L, 999L, 3L, new BigDecimal("1.00"), Instant.now());
    when(properties.isEnabled()).thenReturn(true);
    when(properties.getOrderPaidTopic()).thenReturn(TOPIC);
    when(envelopeFactory.newEventId()).thenReturn("e-2");
    when(envelopeFactory.toJson(any(), any(), any(), anyLong(), any(), any())).thenReturn("{}");
    when(tenantProvider.currentTenantId()).thenReturn(0L);

    writer.appendOrderPaid(event);

    ArgumentCaptor<OrderOutboxRecord> captor = ArgumentCaptor.forClass(OrderOutboxRecord.class);
    verify(outboxRepository).save(captor.capture());
    assertEquals(999L, captor.getValue().getTenantId(), "必须用事件自带租户，避免跨租户误写");
  }

  @Test
  void fallsBackToContextTenantWhenEventHasNone() {
    OrderPaidEvent event = new OrderPaidEvent(1L, null, 3L, new BigDecimal("1.00"), Instant.now());
    when(properties.isEnabled()).thenReturn(true);
    when(properties.getOrderPaidTopic()).thenReturn(TOPIC);
    when(envelopeFactory.newEventId()).thenReturn("e-3");
    when(envelopeFactory.toJson(any(), any(), any(), anyLong(), any(), any())).thenReturn("{}");
    when(tenantProvider.currentTenantId()).thenReturn(42L);

    writer.appendOrderPaid(event);

    ArgumentCaptor<OrderOutboxRecord> captor = ArgumentCaptor.forClass(OrderOutboxRecord.class);
    verify(outboxRepository).save(captor.capture());
    assertEquals(42L, captor.getValue().getTenantId());
  }

  @Test
  void doesNotWriteWhenOutboxDisabled() {
    when(properties.isEnabled()).thenReturn(false);

    writer.appendOrderPaid(new OrderPaidEvent(1L, 7L, 3L, new BigDecimal("1.00"), Instant.now()));

    // 关闭 Outbox 等于声明事件可丢：不得落库，但必须留痕（实现内已打 WARN）
    verify(outboxRepository, never()).save(any());
  }

  @Test
  void ignoresNullEvent() {
    writer.appendOrderPaid(null);

    verify(outboxRepository, never()).save(any());
  }

  @Test
  void appendsStockActionFailedAsIntegrationEventEnvelope() {
    Instant occurredAt = Instant.parse("2026-09-16T03:00:00Z");
    OrderStockActionFailedIntegrationEvent event =
        OrderStockActionFailedIntegrationEvent.fromDomain(
            1L, 7L, 9L, 2, "预留", "库存服务暂时不可用", occurredAt);
    when(properties.isEnabled()).thenReturn(true);
    when(properties.getStockActionFailedTopic())
        .thenReturn("domain.order.order_stock_action_failed.v1");
    when(envelopeFactory.toJson(any(), any(), any(), anyLong(), any(), any())).thenReturn("{}");

    writer.appendStockActionFailed(event);

    ArgumentCaptor<OrderOutboxRecord> recordCaptor =
        ArgumentCaptor.forClass(OrderOutboxRecord.class);
    verify(outboxRepository).save(recordCaptor.capture());
    OrderOutboxRecord record = recordCaptor.getValue();
    assertEquals(OutboxStatus.PENDING, record.getStatus());
    assertEquals("domain.order.order_stock_action_failed.v1", record.getTopic());
    assertEquals("7", record.getPartitionKey(), "分区键取租户，保证同一租户的事件顺序");

    // 库存失败事件是集成事件契约，信封载荷即它本身（非领域事件中转）。
    // eventId 是「租户 × 订单 × 商品 × 动作」派生的确定性幂等键（不是随机 UUID）——
    // 重复投递同一失败事实时，写侧去重与消费端按 eventId 去重同时生效。
    ArgumentCaptor<Object> payloadCaptor = ArgumentCaptor.forClass(Object.class);
    verify(envelopeFactory)
        .toJson(
            eq("OrderStockActionFailedIntegrationEvent-7-1-9-预留"),
            eq("OrderStockActionFailedIntegrationEvent"),
            eq("domain.order.order_stock_action_failed.v1"),
            eq(7L),
            eq(occurredAt),
            payloadCaptor.capture());
    assertEquals(
        "OrderStockActionFailedIntegrationEvent",
        payloadCaptor.getValue().getClass().getSimpleName(),
        "信封载荷必须是库存失败集成事件本身");
    // 确定性键的负向证据：不得再走随机 ID 生成
    verify(envelopeFactory, never()).newEventId();
  }

  @Test
  void stockActionFailedIsIdempotentByBusinessIdentity() {
    OrderStockActionFailedIntegrationEvent first =
        OrderStockActionFailedIntegrationEvent.fromDomain(
            1L, 7L, 9L, 2, "预留", "库存服务暂时不可用", Instant.parse("2026-09-16T03:00:00Z"));
    // 同一事实的重复投递：原因与时间都不同（重试发生在更晚时刻），但业务身份三元组相同
    OrderStockActionFailedIntegrationEvent retry =
        OrderStockActionFailedIntegrationEvent.fromDomain(
            1L, 7L, 9L, 2, "预留", "库存服务仍不可用", Instant.parse("2026-09-16T03:05:00Z"));
    when(properties.isEnabled()).thenReturn(true);
    when(properties.getStockActionFailedTopic())
        .thenReturn("domain.order.order_stock_action_failed.v1");
    when(envelopeFactory.toJson(any(), any(), any(), anyLong(), any(), any())).thenReturn("{}");
    // 首次写入后，按 eventId 能查到 1 条
    when(outboxRepository.countByCriteria(any())).thenReturn(1L);

    writer.appendStockActionFailed(retry);

    verify(outboxRepository, never()).save(any());
    verify(envelopeFactory, never()).toJson(any(), any(), any(), anyLong(), any(), any());
    // 首次（查不到时）仍应落库——幂等不能变成「永不落库」
    when(outboxRepository.countByCriteria(any())).thenReturn(0L);
    writer.appendStockActionFailed(first);
    verify(outboxRepository).save(any());
  }
}
