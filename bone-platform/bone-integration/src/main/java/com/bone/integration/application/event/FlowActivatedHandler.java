package com.bone.integration.application.event;

import com.bone.integration.application.event.support.IntegrationEventFollowUp;
import com.bone.integration.domain.model.flow.event.FlowActivatedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Component
@Slf4j
@RequiredArgsConstructor
public class FlowActivatedHandler {

  private final IntegrationEventFollowUp followUp;

  public void handle(FlowActivatedEvent event) {
    log.info("Flow activated: id={}, name={}", event.flowId(), event.name());
    followUp.notifyInfo(
        "flow.activated.notify", "name=" + event.name(), String.valueOf(event.flowId()));
  }
}
