package com.bone.masterdata.domain.model.entity.event;

import com.bone.core.domain.DomainEvent;
import com.bone.masterdata.domain.model.entity.MasterDataEntity;
import java.time.LocalDateTime;

public record MasterDataEntityPublishedEvent(Long entityId, LocalDateTime publishedAt)
    implements DomainEvent {
  public MasterDataEntityPublishedEvent(MasterDataEntity entity) {
    this(entity.getId(), entity.getUpdatedAt());
  }
}
