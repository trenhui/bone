package com.bone.tpa.intelligent.adjustment.enums;

import lombok.Getter;

/**
 * 意外类型枚举
 */
@Getter
public enum AccidentTypeEnum {

    MEDICAL("MEDICAL", "医疗"),

    ACCIDENT("ACCIDENT", "意外"),

    DISEASE("DISEASE", "疾病"),

    LIFE_INSURANCE("LIFE_INSURANCE", "人寿"),

    OTHER("OTHER", "其他"),

    ;

    private final String code;
    private final String value;

    AccidentTypeEnum(String code, String value) {
        this.code = code;
        this.value = value;
    }

    public static AccidentTypeEnum getByCode(String code) {
        for (AccidentTypeEnum e : AccidentTypeEnum.values()) {
            if (e.getCode().equals(code)) {
                return e;
            }
        }
        return null;
    }
}
