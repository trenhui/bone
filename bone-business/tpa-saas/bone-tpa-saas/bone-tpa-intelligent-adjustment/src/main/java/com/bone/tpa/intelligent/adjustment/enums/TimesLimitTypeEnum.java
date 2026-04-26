package com.bone.tpa.intelligent.adjustment.enums;

import lombok.Getter;

/**
 * 次日限额类型
 */
@Getter
public enum TimesLimitTypeEnum {

    TIMES("TIMES", "按次"),

    DAYS("DAYS", "按日"),

    MONTHS("MONTHS", "按月"),

    ;

    private final String code;
    private final String value;

    TimesLimitTypeEnum(String code, String value) {
        this.code = code;
        this.value = value;
    }

    public static TimesLimitTypeEnum getByCode(String code) {
        for (TimesLimitTypeEnum e : TimesLimitTypeEnum.values()) {
            if (e.getCode().equals(code)) {
                return e;
            }
        }
        return null;
    }
}
