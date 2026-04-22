package com.bone.masterdata.domain.model.field.vo;

import com.bone.core.exception.BizException;

public record FieldCode(String value) {
    public FieldCode {
        if (value == null || value.isBlank()) {
            throw BizException.of("字段编码不能为空");
        }
        if (value.length() < 1 || value.length() > 50) {
            throw BizException.of("字段编码长度必须在1-50之间");
        }
        if (!value.matches("^[a-zA-Z][a-zA-Z0-9_]*$")) {
            throw BizException.of("字段编码只能包含字母、数字和下划线，且以字母开头");
        }
    }

    public static FieldCode of(String value) {
        return new FieldCode(value);
    }
}
