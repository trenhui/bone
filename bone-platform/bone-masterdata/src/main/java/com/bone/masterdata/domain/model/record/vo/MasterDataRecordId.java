package com.bone.masterdata.domain.model.record.vo;

import com.bone.core.exception.DomainException;

public record MasterDataRecordId(Long value) {
    public MasterDataRecordId {
        if (value == null || value <= 0) {
            throw new DomainException("主数据记录ID不能为空且必须大于0");
        }
    }

    public static MasterDataRecordId of(Long value) {
        return new MasterDataRecordId(value);
    }
}