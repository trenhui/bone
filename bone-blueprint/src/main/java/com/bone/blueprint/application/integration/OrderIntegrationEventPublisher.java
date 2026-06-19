package com.bone.blueprint.application.integration;

import com.bone.blueprint.application.config.OrderOutboxProperties;
import com.bone.blueprint.application.event.outbox.OrderOutboxEnvelopeFactory;
import com.bone.blueprint.application.integration.event.OrderPaidIntegrationEvent;
import com.bone.blueprint.application.integration.port.OrderMessageSender;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** 集成事件出站（经 {@link OrderMessageSender} 投递，通常由 Outbox 中继调用）。 */
@Component
@RequiredArgsConstructor
public class OrderIntegrationEventPublisher {

  private final OrderOutboxProperties properties;
  private final OrderMessageSender messageSender;
  private final OrderOutboxEnvelopeFactory envelopeFactory;

  public void publishOrderPaid(OrderPaidIntegrationEvent event) {
    String json = envelopeFactory.toJson(event);
    String partitionKey = String.valueOf(event.tenantId() != null ? event.tenantId() : 0L);
    messageSender.send(properties.getOrderPaidTopic(), partitionKey, json);
  }
}
