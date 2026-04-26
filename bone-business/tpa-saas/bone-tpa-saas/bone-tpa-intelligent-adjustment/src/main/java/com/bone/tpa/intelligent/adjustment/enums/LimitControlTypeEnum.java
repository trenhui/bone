package com.bone.tpa.intelligent.adjustment.enums;

import lombok.Getter;

/**
 * 控额方式枚举
 */
@Getter
public enum LimitControlTypeEnum {


    LIABILITY("LIABILITY", "责任额度"),

    LIABILITY_VISIT("LIABILITY_VISIT", "责任额度+就诊类别"),

    LIABILITY_PERSONAL("LIABILITY_PERSONAL", "责任额度+个人额度"),

    PERSONAL("PERSONAL", "个人额度"),

    FIXED_AMOUNT("FIXED_AMOUNT", "给付基础额度"),

    ;

    private final String code;
    private final String value;

    LimitControlTypeEnum(String code, String value) {
        this.code = code;
        this.value = value;
    }

    public static LimitControlTypeEnum getByCode(String code) {
        for (LimitControlTypeEnum e : LimitControlTypeEnum.values()) {
            if (e.getCode().equals(code)) {
                return e;
            }
        }
        return null;
    }
}
