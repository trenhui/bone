package com.bone.masterdata.domain.model.entity.vo;

import com.bone.core.exception.DomainException;

public record MasterDataEntityName(String value) {
    public MasterDataEntityName {
        if (value == null || value.isBlank()) {
            throw new DomainException("主数据实体名称不能为空");
        }
        if (value.length() < 2 || value.length() > 50) {
            throw new DomainException("主数据实体名称长度必须在2-50字符之间");
        }
    }

    public static MasterDataEntityName of(String value) {
        return new MasterDataEntityName(value);
    }
}