package com.bone.masterdata.domain.model.entity.event;

import com.bone.core.domain.DomainEvent;
import com.bone.masterdata.domain.model.entity.MasterDataEntity;
import com.bone.masterdata.domain.model.entity.vo.MasterDataEntityId;
import com.bone.masterdata.domain.model.entity.vo.MasterDataEntityName;

public record MasterDataEntityCreatedEvent(MasterDataEntityId entityId, MasterDataEntityName entityName) implements DomainEvent {
    public MasterDataEntityCreatedEvent(MasterDataEntity entity) {
        this(entity.getId(), entity.getName());
    }
}