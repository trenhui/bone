package com.bone.masterdata.domain.model.field.vo;

import com.bone.core.exception.BizException;

public record MasterDataFieldId(Long value) {
    public MasterDataFieldId {
        if (value == null || value <= 0) {
            throw BizException.of("主数据字段ID必须大于0");
        }
    }

    public static MasterDataFieldId of(Long value) {
        return new MasterDataFieldId(value);
    }
}
