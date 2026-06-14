package com.bone.masterdata.domain.model.entity.event;

import com.bone.core.domain.DomainEvent;
import com.bone.masterdata.domain.entity.MasterDataEntity;

public record MasterDataEntityCreatedEvent(Long entityId, String entityName)
    implements DomainEvent {
  public MasterDataEntityCreatedEvent(MasterDataEntity entity) {
    this(entity.getId(), entity.getName().value());
  }
}
