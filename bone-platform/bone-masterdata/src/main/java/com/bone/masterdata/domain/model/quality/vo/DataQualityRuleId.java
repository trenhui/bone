package com.bone.masterdata.domain.model.quality.vo;

import com.bone.core.exception.DomainException;

public record DataQualityRuleId(Long value) {
    public DataQualityRuleId {
        if (value == null || value <= 0) {
            throw new DomainException("数据质量规则ID不能为空且必须大于0");
        }
    }

    public static DataQualityRuleId of(Long value) {
        return new DataQualityRuleId(value);
    }
}