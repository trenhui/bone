package com.bone.lowcode.infra.domain.valueobject;

import lombok.Getter;

//字段类型枚举
@Getter
public enum FieldTypeEnum {
    SYSTEM((byte) 1, "系统字段"),
    BIZ((byte)2, "专属字段"),

    ;

    private final byte code;
    private final String desc;

    FieldTypeEnum(byte code, String desc) {
        this.code = code;
        this.desc = desc;
    }

    public static String getDescByCode(byte code) {
        for (FieldTypeEnum value : FieldTypeEnum.values()) {
            if (value.getCode() == code) return value.getDesc();
        }
        return null;
    }
}
