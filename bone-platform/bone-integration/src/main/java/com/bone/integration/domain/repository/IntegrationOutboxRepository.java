package com.bone.integration.domain.repository;

import com.bone.integration.domain.outbox.IntegrationOutboxRecord;
import com.bone.metadata.sdk.Repository;

public interface IntegrationOutboxRepository extends Repository<IntegrationOutboxRecord, Long> {}
