package com.bone.tpa.intelligent.adjustment.converter;

import com.bone.tpa.sdk.adjustment.model.AdjustmentRecord;
import com.bone.tpa.sdk.adjustment.response.InvoiceAdjustmentResponse;
import org.mapstruct.Mapper;
import org.mapstruct.MappingConstants;

@Mapper(componentModel = MappingConstants.ComponentModel.SPRING)
public interface AdjustmentRecordConvert {

    InvoiceAdjustmentResponse toDto(AdjustmentRecord adjustmentRecord);

}
