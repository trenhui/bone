package com.bone.integration.domain.model.flow.event;

import com.bone.core.domain.DomainEvent;
import com.bone.integration.domain.model.flow.IntegrationFlow;

public record FlowCreatedEvent(Long flowId, String name, String description) implements DomainEvent {
    public FlowCreatedEvent(IntegrationFlow flow) {
        this(flow.getId().value(), flow.getName(), flow.getDescription());
    }
}