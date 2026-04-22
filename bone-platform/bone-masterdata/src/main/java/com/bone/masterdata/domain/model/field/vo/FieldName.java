package com.bone.masterdata.domain.model.field.vo;

import com.bone.core.exception.BizException;

public record FieldName(String value) {
    public FieldName {
        if (value == null || value.isBlank()) {
            throw BizException.of("字段名称不能为空");
        }
        if (value.length() < 1 || value.length() > 100) {
            throw BizException.of("字段名称长度必须在1-100之间");
        }
    }

    public static FieldName of(String value) {
        return new FieldName(value);
    }
}
