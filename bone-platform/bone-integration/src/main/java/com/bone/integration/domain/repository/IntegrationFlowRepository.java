package com.bone.integration.domain.repository;

import com.bone.integration.domain.model.flow.IntegrationFlow;
import com.bone.metadata.sdk.Repository;

public interface IntegrationFlowRepository extends Repository<IntegrationFlow, Long> {
    boolean existsByName(String name);
    IntegrationFlow findByName(String name);
}