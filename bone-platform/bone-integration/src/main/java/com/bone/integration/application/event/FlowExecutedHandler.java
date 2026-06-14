package com.bone.integration.application.event;

import com.bone.integration.application.event.support.IntegrationEventFollowUp;
import com.bone.integration.domain.model.flow.event.FlowExecutedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Component
@Slf4j
@RequiredArgsConstructor
public class FlowExecutedHandler {

  private final IntegrationEventFollowUp followUp;

  public void handle(FlowExecutedEvent event) {
    log.info(
        "Flow executed: id={}, executionId={}, success={}, message={}",
        event.flowId(),
        event.executionId(),
        event.success(),
        event.message());
    if (!event.success()) {
      followUp.notifyHigh(
          "flow.execution.failed.alert",
          "executionId=" + event.executionId() + ", message=" + event.message(),
          String.valueOf(event.flowId()));
    }
  }
}
