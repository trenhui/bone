package com.bone.integration.domain.model.execution.event;

import com.bone.core.domain.DomainEvent;

public record ExecutionStartedEvent(Long executionId, Long flowId, String inputData) implements DomainEvent {
}