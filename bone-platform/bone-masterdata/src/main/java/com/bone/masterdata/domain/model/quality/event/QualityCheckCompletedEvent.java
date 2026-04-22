package com.bone.masterdata.domain.model.quality.event;

import com.bone.core.domain.DomainEvent;
import com.bone.masterdata.domain.model.entity.vo.MasterDataEntityId;
import com.bone.masterdata.domain.model.quality.QualityCheck;
import com.bone.masterdata.domain.model.quality.vo.QualityCheckId;

public record QualityCheckCompletedEvent(QualityCheckId checkId, MasterDataEntityId entityId, Integer totalRecords, Integer failedRecords) implements DomainEvent {
    public QualityCheckCompletedEvent(QualityCheck check) {
        this(check.getId(), check.getMasterDataEntityId(), check.getTotalRecords(), check.getFailedRecords());
    }
}