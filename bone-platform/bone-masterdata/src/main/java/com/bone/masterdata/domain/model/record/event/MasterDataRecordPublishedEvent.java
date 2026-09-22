package com.bone.masterdata.domain.model.record.event;

import com.bone.core.domain.DomainEvent;
import com.bone.masterdata.domain.model.record.MasterDataRecord;
import java.time.LocalDateTime;

public record MasterDataRecordPublishedEvent(
    Long recordId, Long entityId, LocalDateTime publishTime) implements DomainEvent {
  public MasterDataRecordPublishedEvent(MasterDataRecord record) {
    this(record.getId(), record.getMasterDataEntityId(), record.getPublishTime());
  }
}
