package com.bone.masterdata.domain.model.record.event;

import com.bone.core.domain.DomainEvent;
import com.bone.masterdata.domain.record.MasterDataRecord;

public record MasterDataRecordCreatedEvent(Long recordId, Long entityId) implements DomainEvent {
    public MasterDataRecordCreatedEvent(MasterDataRecord record) {
        this(record.getId(), record.getMasterDataEntityId());
    }
}