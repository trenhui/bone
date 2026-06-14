package com.bone.integration.domain.model.connector.event;

import com.bone.core.domain.DomainEvent;

public record ConnectorTestedEvent(Long connectorId, boolean success, String message)
    implements DomainEvent {}
