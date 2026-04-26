package com.bone.tpa.intelligent.adjustment.enums;

import lombok.Getter;

/**
 * 医保使用情形枚举
 */
@Getter
public enum MedicalInsuranceConditionEnum {

    AFTER("AFTER", "医保后才能赔付"),

    BEFORE("BEFORE", "未使用医保赔付"),

    BOTH("BOTH", "两种都能赔付"),

    ;

    private final String code;
    private final String value;

    MedicalInsuranceConditionEnum(String code, String value) {
        this.code = code;
        this.value = value;
    }

    public static MedicalInsuranceConditionEnum getByCode(String code) {
        for (MedicalInsuranceConditionEnum e : MedicalInsuranceConditionEnum.values()) {
            if (e.getCode().equals(code)) {
                return e;
            }
        }
        return null;
    }
}
