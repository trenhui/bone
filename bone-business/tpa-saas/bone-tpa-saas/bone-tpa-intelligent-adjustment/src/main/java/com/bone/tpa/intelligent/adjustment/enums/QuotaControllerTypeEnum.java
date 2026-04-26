package com.bone.tpa.intelligent.adjustment.enums;

import lombok.Getter;

/**
 * 控额方式枚举
 */
@Getter
public enum QuotaControllerTypeEnum {

    LIABILITY("LIABILITY", "责任额度"),

    PERSONAL("PERSONAL", "个人额度"),

    COVERAGE("COVERAGE", "险种额度"),

    PLAN("PLAN", "计划额度"),

    VISIT("VISIT", "就诊类别"),

    SHARING("SHARING", "共保额度"),

    TIMES("TIMES", "次期限额"),

    FEE_TYPE("FEE_TYPE", "费用类型"),


    DIRECT_PUBLIC("DIRECT_PUBLIC", "直付公账额度"),

    DIRECT_PERSONAL("DIRECT_PERSONAL", "直付个账额度"),

    ;

    private final String code;
    private final String value;

    QuotaControllerTypeEnum(String code, String value) {
        this.code = code;
        this.value = value;
    }

    public static QuotaControllerTypeEnum getByCode(String code) {
        for (QuotaControllerTypeEnum e : QuotaControllerTypeEnum.values()) {
            if (e.getCode().equals(code)) {
                return e;
            }
        }
        return null;
    }
}
