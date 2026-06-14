package com.bone.integration.domain.model.flow.event;

import com.bone.core.domain.DomainEvent;

public record FlowExecutedEvent(Long flowId, Long executionId, boolean success, String message)
    implements DomainEvent {}
