package com.bone.integration.domain.model.connector.event;

import com.bone.core.domain.DomainEvent;
import com.bone.integration.domain.connector.Connector;

public record ConnectorCreatedEvent(Long connectorId, String name, String type) implements DomainEvent {
    public ConnectorCreatedEvent(Connector connector) {
        this(connector.getId(), connector.getName(), connector.getType().name());
    }
}