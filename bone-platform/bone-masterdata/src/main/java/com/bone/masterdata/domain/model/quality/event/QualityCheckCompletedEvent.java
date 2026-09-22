package com.bone.masterdata.domain.model.quality.event;

import com.bone.core.domain.DomainEvent;
import com.bone.masterdata.domain.model.quality.QualityCheck;

public record QualityCheckCompletedEvent(
    Long checkId, Long entityId, Integer totalRecords, Integer failedRecords)
    implements DomainEvent {
  public QualityCheckCompletedEvent(QualityCheck check) {
    this(
        check.getId(),
        check.getMasterDataEntityId(),
        check.getTotalRecords(),
        check.getFailedRecords());
  }
}
