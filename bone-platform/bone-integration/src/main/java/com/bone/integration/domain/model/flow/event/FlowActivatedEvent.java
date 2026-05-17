package com.bone.integration.domain.model.flow.event;

import com.bone.core.domain.DomainEvent;
import com.bone.integration.domain.flow.IntegrationFlow;

public record FlowActivatedEvent(Long flowId, String name) implements DomainEvent {
    public FlowActivatedEvent(IntegrationFlow flow) {
        this(flow.getId(), flow.getName());
    }
}