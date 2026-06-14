package com.bone.integration.application.event;

import com.bone.integration.application.event.support.IntegrationEventFollowUp;
import com.bone.integration.domain.model.connector.event.ConnectorCreatedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Component
@Slf4j
@RequiredArgsConstructor
public class ConnectorCreatedHandler {

  private final IntegrationEventFollowUp followUp;

  public void handle(ConnectorCreatedEvent event) {
    log.info(
        "Connector created: id={}, name={}, type={}",
        event.connectorId(),
        event.name(),
        event.type());
    followUp.notifyInfo(
        "connector.created.notify",
        "name=" + event.name() + ", type=" + event.type(),
        String.valueOf(event.connectorId()));
  }
}
