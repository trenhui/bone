package com.bone.integration.application.event;

import com.bone.integration.application.event.support.IntegrationEventFollowUp;
import com.bone.integration.domain.model.execution.event.ExecutionStartedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Component
@Slf4j
@RequiredArgsConstructor
public class ExecutionStartedHandler {

    private final IntegrationEventFollowUp followUp;

    public void handle(ExecutionStartedEvent event) {
        log.info(
                "Execution started: executionId={}, flowId={}",
                event.executionId(),
                event.flowId());
        followUp.notifyInfo(
                "execution.started.notify",
                "flowId=" + event.flowId(),
                String.valueOf(event.executionId()));
    }
}
