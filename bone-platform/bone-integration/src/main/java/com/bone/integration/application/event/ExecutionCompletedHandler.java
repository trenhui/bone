package com.bone.integration.application.event;

import com.bone.integration.application.event.support.IntegrationEventFollowUp;
import com.bone.integration.domain.model.execution.event.ExecutionCompletedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Component
@Slf4j
@RequiredArgsConstructor
public class ExecutionCompletedHandler {

    private final IntegrationEventFollowUp followUp;

    public void handle(ExecutionCompletedEvent event) {
        log.info(
                "Execution completed: executionId={}, flowId={}, success={}",
                event.executionId(),
                event.flowId(),
                event.success());
        if (!event.success()) {
            followUp.notifyHigh(
                    "execution.failed.alert",
                    "flowId=" + event.flowId() + ", error=" + event.errorMessage(),
                    String.valueOf(event.executionId()));
        } else {
            log.debug(
                    "Execution metrics placeholder: executionId={}, flowId={}",
                    event.executionId(),
                    event.flowId());
        }
    }
}
