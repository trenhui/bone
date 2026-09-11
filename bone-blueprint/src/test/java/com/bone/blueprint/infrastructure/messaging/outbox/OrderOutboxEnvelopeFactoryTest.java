package com.bone.blueprint.infrastructure.messaging.outbox;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import com.bone.blueprint.domain.integration.event.OrderPaidIntegrationEvent;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.time.Instant;
import org.junit.jupiter.api.Test;

/**
 * Outbox 信封契约测试：信封必须携带 {@code eventId}。
 *
 * <p>回归防护：Outbox 是至少一次投递，消费端只能凭 eventId 去重。若信封里没有 eventId， 「消费端幂等」这条要求在物理上就不可能满足（P-5.4 / E-9.6.3）。
 */
class OrderOutboxEnvelopeFactoryTest {

  private final OrderOutboxEnvelopeFactory factory = new OrderOutboxEnvelopeFactory();

  @Test
  void testEnvelopeCarriesEventIdAndPayload() throws Exception {
    OrderPaidIntegrationEvent event =
        OrderPaidIntegrationEvent.fromDomain(1L, 2L, 3L, new BigDecimal("200"), Instant.now());
    String eventId = factory.newEventId();

    String json = factory.toJson(eventId, event);

    JsonNode envelope = new ObjectMapper().readTree(json);
    assertEquals(eventId, envelope.path("eventId").asText());
    assertEquals(1L, envelope.path("payload").path("orderId").asLong());
    assertEquals("1.0", envelope.path("payload").path("schemaVersion").asText());
    assertNotNull(eventId);
  }
}
