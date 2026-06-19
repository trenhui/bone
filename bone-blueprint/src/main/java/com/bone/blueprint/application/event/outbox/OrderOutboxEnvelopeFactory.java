package com.bone.blueprint.application.event.outbox;

import com.bone.blueprint.application.integration.event.OrderPaidIntegrationEvent;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class OrderOutboxEnvelopeFactory {

  private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());

  public String newEventId() {
    return UUID.randomUUID().toString();
  }

  public String toJson(OrderPaidIntegrationEvent event) {
    try {
      return objectMapper.writeValueAsString(event);
    } catch (JsonProcessingException e) {
      throw new IllegalStateException("Outbox 信封序列化失败", e);
    }
  }
}
