package com.bone.masterdata.domain.model.entity.event;

import com.bone.core.domain.DomainEvent;
import com.bone.masterdata.domain.model.entity.MasterDataField;

public record MasterDataFieldAddedEvent(Long fieldId, Long entityId, String fieldName) implements DomainEvent {
    public MasterDataFieldAddedEvent(MasterDataField field) {
        this(field.getId(), field.getMasterDataEntityId(), field.getName().value());
    }
}