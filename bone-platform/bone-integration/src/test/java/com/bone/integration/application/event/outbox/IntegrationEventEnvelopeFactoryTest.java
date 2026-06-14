package com.bone.integration.application.event.outbox;

import static org.assertj.core.api.Assertions.assertThat;

import com.bone.integration.domain.model.flow.event.FlowActivatedEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

class IntegrationEventEnvelopeFactoryTest {

  private final IntegrationEventEnvelopeFactory factory =
      new IntegrationEventEnvelopeFactory(new ObjectMapper());

  @Test
  void create_buildsEnvelopeWithRegisteredEventType() {
    IntegrationEventRegistration registration =
        new IntegrationEventRegistration("FlowActivated", "domain.integration.flow_activated.v1");
    FlowActivatedEvent event = new FlowActivatedEvent(10L, "demo");

    IntegrationEventEnvelope envelope = factory.create(event, registration);

    assertThat(envelope.eventType()).isEqualTo("FlowActivated");
    assertThat(envelope.topic()).isEqualTo("domain.integration.flow_activated.v1");
    assertThat(envelope.schemaVersion()).isEqualTo("1.0");
    assertThat(envelope.payload()).containsEntry("flowId", 10L).containsEntry("name", "demo");
    assertThat(envelope.eventId()).isNotBlank();
    assertThat(envelope.traceId()).isNotBlank();
  }
}
