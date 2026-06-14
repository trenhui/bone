package com.bone.integration.domain.model.execution.event;

import com.bone.core.domain.DomainEvent;

public record ExecutionCompletedEvent(
    Long executionId, Long flowId, boolean success, String outputData, String errorMessage)
    implements DomainEvent {}
