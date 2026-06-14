package com.bone.integration.application.event.outbox;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.bone.integration.application.config.IntegrationOutboxProperties;
import com.bone.integration.application.event.port.IntegrationMessageSender;
import com.bone.integration.domain.outbox.IntegrationOutboxRecord;
import com.bone.integration.domain.outbox.OutboxStatus;
import com.bone.integration.domain.repository.IntegrationOutboxRepository;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class IntegrationOutboxRelayTest {

  @Mock private IntegrationOutboxRepository outboxRepository;

  @Mock private IntegrationMessageSender messageSender;

  private IntegrationOutboxRelay relay;

  @BeforeEach
  void setUp() {
    IntegrationOutboxProperties properties = new IntegrationOutboxProperties();
    properties.setEnabled(true);
    properties.setBatchSize(10);
    properties.setMaxRetries(3);
    relay = new IntegrationOutboxRelay(properties, outboxRepository, messageSender);
  }

  @Test
  void relayBatch_marksRecordSentOnSuccess() {
    IntegrationOutboxRecord record =
        IntegrationOutboxRecord.pending(
            1L, 0L, "evt-1", "FlowActivated", "domain.integration.flow_activated.v1", "0", "{}");
    when(outboxRepository.findByCriteria(any())).thenReturn(List.of(record));

    int sent = relay.relayBatch();

    assertThat(sent).isEqualTo(1);
    assertThat(record.getStatus()).isEqualTo(OutboxStatus.SENT);
    verify(messageSender)
        .send(record.getTopic(), record.getPartitionKey(), record.getEnvelopeJson());
    verify(outboxRepository).update(record);
  }

  @Test
  void relayBatch_incrementsRetryOnFailure() {
    IntegrationOutboxRecord record =
        IntegrationOutboxRecord.pending(
            2L, 0L, "evt-2", "FlowCreated", "domain.integration.flow_created.v1", "0", "{}");
    when(outboxRepository.findByCriteria(any())).thenReturn(List.of(record));
    org.mockito.Mockito.doThrow(new RuntimeException("mq down"))
        .when(messageSender)
        .send(any(), any(), any());

    int sent = relay.relayBatch();

    assertThat(sent).isZero();
    assertThat(record.getRetryCount()).isEqualTo(1);
    assertThat(record.getStatus()).isEqualTo(OutboxStatus.PENDING);
    ArgumentCaptor<IntegrationOutboxRecord> captor =
        ArgumentCaptor.forClass(IntegrationOutboxRecord.class);
    verify(outboxRepository).update(captor.capture());
    assertThat(captor.getValue().getRetryCount()).isEqualTo(1);
  }
}
