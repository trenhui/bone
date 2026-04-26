package com.bone.tpa.intelligent.adjustment.enums;

import lombok.Getter;

/**
 * 津贴给付用
 * 开始日期非保险期间或等待期内，处理方法
 */
@Getter
public enum AllowanceStartOutPeriodEnum {

    ALLOW("ALLOW", "期间可赔"),

    DISALLOW("DISALLOW", "拒赔处理")

    ;

    private final String code;
    private final String value;

    AllowanceStartOutPeriodEnum(String code, String value) {
        this.code = code;
        this.value = value;
    }

    public static AllowanceStartOutPeriodEnum getByCode(String code) {
        for (AllowanceStartOutPeriodEnum e : AllowanceStartOutPeriodEnum.values()) {
            if (e.getCode().equals(code)) {
                return e;
            }
        }
        return null;
    }
}
