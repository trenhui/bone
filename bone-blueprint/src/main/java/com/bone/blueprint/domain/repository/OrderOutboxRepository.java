package com.bone.blueprint.domain.repository;

import com.bone.blueprint.domain.outbox.OrderOutboxRecord;
import com.bone.metadata.sdk.Repository;

public interface OrderOutboxRepository extends Repository<OrderOutboxRecord, Long> {
}
