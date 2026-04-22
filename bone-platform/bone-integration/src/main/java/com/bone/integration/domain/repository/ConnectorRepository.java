package com.bone.integration.domain.repository;

import com.bone.integration.domain.model.connector.Connector;
import com.bone.metadata.sdk.Repository;

public interface ConnectorRepository extends Repository<Connector, Long> {
    boolean existsByName(String name);
    Connector findByName(String name);
}