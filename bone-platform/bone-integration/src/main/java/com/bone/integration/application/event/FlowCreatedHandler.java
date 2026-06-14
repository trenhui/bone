package com.bone.integration.application.event;

import com.bone.integration.application.event.support.IntegrationEventFollowUp;
import com.bone.integration.domain.model.flow.event.FlowCreatedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Component
@Slf4j
@RequiredArgsConstructor
public class FlowCreatedHandler {

  private final IntegrationEventFollowUp followUp;

  public void handle(FlowCreatedEvent event) {
    log.info("Flow created: flowId={}, name={}", event.flowId(), event.name());
    followUp.notifyInfo(
        "flow.created.notify", "name=" + event.name(), String.valueOf(event.flowId()));
    log.debug("Flow created audit placeholder: flowId={}", event.flowId());
  }
}
