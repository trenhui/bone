package com.bone.masterdata.domain.model.field.event;

import com.bone.core.domain.DomainEvent;
import com.bone.masterdata.domain.entity.MasterDataField;

public record MasterDataFieldAddedEvent(Long entityId, Long fieldId, String fieldName)
    implements DomainEvent {
  public MasterDataFieldAddedEvent(MasterDataField field) {
    this(field.getMasterDataEntityId(), field.getId(), field.getName().value());
  }
}
