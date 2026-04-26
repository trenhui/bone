package com.bone.tpa.api.enums;

import lombok.Getter;

@Getter
public enum SourceType {
    线上("0","线上"),
    线下("1","线下")
    ;
    private String code ;
    private String desc;

    SourceType(String code, String desc) {
        this.code = code;
        this.desc = desc;
    }

    public static SourceType getEnumCode(String code) {
        for (SourceType sourceType : SourceType.values()) {
            if (sourceType.getCode().equals(code)) {
                return sourceType;
            }
        }
        return null;
    }

    public static SourceType getEnumDesc(String desc) {
        for (SourceType sourceType : SourceType.values()) {
            if (sourceType.getDesc().equals(desc)) {
                return sourceType;
            }
        }
        return null;
    }
}
