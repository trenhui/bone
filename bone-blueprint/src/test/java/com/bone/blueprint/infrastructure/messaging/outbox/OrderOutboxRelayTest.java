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
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class OrderOutboxRelayTest {

  @Mock private OrderOutboxProperties properties;

  @Mock private OrderOutboxRepository outboxRepository;

  @Mock private OrderMessageSender messageSender;

  @InjectMocks private OrderOutboxRelay relay;

  private OrderOutboxRecord record;

  @BeforeEach
  void setUp() {
    when(properties.isEnabled()).thenReturn(true);
    when(properties.getBatchSize()).thenReturn(10);
    when(properties.getMaxRetries()).thenReturn(3);
    record =
        OrderOutboxRecord.pending(
            1L,
            100L,
            "evt-1",
            "OrderPaidIntegrationEvent",
            "bone.order.paid",
            "100",
            "{\"orderId\":1}");
  }

  @Test
  void relayPendingMarksSentOnSuccess() {
    when(outboxRepository.findByCriteria(any())).thenReturn(List.of(record));

    int sent = relay.relayPending();

    assertEquals(1, sent);
    verify(messageSender, times(1)).send("bone.order.paid", "100", record.getEnvelopeJson());
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
  }

  @Test
  void relayPendingMarksFailedAfterMaxRetries() {
    record.incrementRetry();
    record.incrementRetry();
    when(outboxRepository.findByCriteria(any())).thenReturn(List.of(record));
    doThrow(new RuntimeException("mq down")).when(messageSender).send(any(), any(), any());

    relay.relayPending();

    assertEquals(OutboxStatus.FAILED, record.getStatus());
  }

  @Test
  void relayPendingSkipsWhenDisabled() {
    when(properties.isEnabled()).thenReturn(false);

    int sent = relay.relayPending();

    assertEquals(0, sent);
    verify(outboxRepository, never()).findByCriteria(any());
  }
}
