package com.bone.masterdata.domain.model.field.event;

import com.bone.masterdata.domain.model.entity.vo.MasterDataEntityId;
import com.bone.masterdata.domain.model.field.vo.FieldName;
import com.bone.masterdata.domain.model.field.vo.MasterDataFieldId;

public record MasterDataFieldAddedEvent(
    MasterDataEntityId entityId, MasterDataFieldId fieldId, FieldName name) {}
