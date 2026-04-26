package com.bone.tpa.intelligent.adjustment.enums;

import lombok.Getter;

/**
 * 出险类型枚举
 */
@Getter
public enum OutInsureTypeEnum {

    ACCIDENTAL_DEATH("ACCIDENTAL_DEATH", "意外身故"),

    ACCIDENTAL_MEDICAL("ACCIDENTAL_MEDICAL", "意外医疗"),

    ACCIDENTAL_DISABILITY("ACCIDENTAL_DISABILITY", "意外伤残"),

    ;

    private final String code;
    private final String value;

    OutInsureTypeEnum(String code, String value) {
        this.code = code;
        this.value = value;
    }

    public static OutInsureTypeEnum getByCode(String code) {
        for (OutInsureTypeEnum e : OutInsureTypeEnum.values()) {
            if (e.getCode().equals(code)) {
                return e;
            }
        }
        return null;
    }
}
