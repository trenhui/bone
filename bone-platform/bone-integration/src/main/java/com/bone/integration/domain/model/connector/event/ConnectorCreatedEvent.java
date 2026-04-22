package com.bone.integration.domain.model.connector.event;

import com.bone.core.domain.DomainEvent;
import com.bone.integration.domain.model.connector.Connector;

public record ConnectorCreatedEvent(Long connectorId, String name, String type) implements DomainEvent {
    public ConnectorCreatedEvent(Connector connector) {
        this(connector.getId().value(), connector.getName(), connector.getType().name());
    }
}