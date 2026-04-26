package com.bone.tpa.intelligent.adjustment.enums;

import lombok.Getter;

/**
 * 限定适用范围使用的适用范围枚举
 */
@Getter
public enum RestrictRangeEnum {

    HOSPITAL("HOSPITAL", "医院限定"),

    DRUG("DRUG", "药品限定"),

    DIAGNOSE("DIAGNOSE", "诊疗限定"),

    DISEASE("DISEASE", "病种限定"),

            ;

    private final String code;
    private final String value;

    RestrictRangeEnum(String code, String value) {
        this.code = code;
        this.value = value;
    }

    public static RestrictRangeEnum getByCode(String code) {
        for (RestrictRangeEnum e : RestrictRangeEnum.values()) {
            if (e.getCode().equals(code)) {
                return e;
            }
        }
        return null;
    }
}
