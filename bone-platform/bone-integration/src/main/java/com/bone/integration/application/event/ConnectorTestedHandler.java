package com.bone.integration.application.event;

import com.bone.integration.application.event.support.IntegrationEventFollowUp;
import com.bone.integration.domain.model.connector.event.ConnectorTestedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Component
@Slf4j
@RequiredArgsConstructor
public class ConnectorTestedHandler {

  private final IntegrationEventFollowUp followUp;

  public void handle(ConnectorTestedEvent event) {
    log.info(
        "Connector tested: connectorId={}, success={}, message={}",
        event.connectorId(),
        event.success(),
        event.message());
    if (!event.success()) {
      followUp.notifyHigh(
          "connector.test.failed.alert", event.message(), String.valueOf(event.connectorId()));
    }
  }
}
