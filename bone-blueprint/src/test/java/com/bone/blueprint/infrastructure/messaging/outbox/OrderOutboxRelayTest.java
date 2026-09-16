package com.bone.blueprint.infrastructure.messaging.outbox;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.bone.blueprint.application.port.out.OrderMessageSender;
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

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class OrderOutboxRelayTest {

  private static final String TOPIC = "domain.order.order_paid.v1";

  private static final String DEAD_LETTER_TOPIC = "platform.dead_letter.v1";

  @Mock private OrderOutboxProperties properties;

  @Mock private OrderOutboxRepository outboxRepository;

  @Mock private OrderMessageSender messageSender;

  /** 真实注册表（非 mock）：{@code counter(...)} 需要返回可 increment 的对象。 */
  private final MeterRegistry meterRegistry = new SimpleMeterRegistry();

  private OrderOutboxRelay relay;

  private OrderOutboxRecord record;

  @BeforeEach
  void setUp() {
    relay = new OrderOutboxRelay(properties, outboxRepository, messageSender, meterRegistry);
    when(properties.isEnabled()).thenReturn(true);
    when(properties.getBatchSize()).thenReturn(10);
    when(properties.getMaxRetries()).thenReturn(3);
    when(properties.getDeadLetterTopic()).thenReturn(DEAD_LETTER_TOPIC);
    record =
        OrderOutboxRecord.pending(
            1L, 100L, "evt-1", "OrderPaidIntegrationEvent", TOPIC, "100", "{\"orderId\":1}");
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

  @Test
  void relayPendingSkipsWhenDisabled() {
    when(properties.isEnabled()).thenReturn(false);

    int sent = relay.relayPending();

    assertEquals(0, sent);
    verify(outboxRepository, never()).findByCriteria(any());
  }
}
