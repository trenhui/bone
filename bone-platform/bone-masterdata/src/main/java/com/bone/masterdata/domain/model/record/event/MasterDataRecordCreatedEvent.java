package com.bone.masterdata.domain.model.record.event;

import com.bone.core.domain.DomainEvent;
import com.bone.masterdata.domain.model.entity.vo.MasterDataEntityId;
import com.bone.masterdata.domain.model.record.MasterDataRecord;
import com.bone.masterdata.domain.model.record.vo.MasterDataRecordId;

public record MasterDataRecordCreatedEvent(MasterDataRecordId recordId, MasterDataEntityId entityId) implements DomainEvent {
    public MasterDataRecordCreatedEvent(MasterDataRecord record) {
        this(record.getId(), record.getMasterDataEntityId());
    }
}