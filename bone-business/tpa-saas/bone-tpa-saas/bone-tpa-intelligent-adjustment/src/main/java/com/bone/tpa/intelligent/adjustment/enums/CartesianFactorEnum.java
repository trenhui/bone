package com.bone.tpa.intelligent.adjustment.enums;

import lombok.Getter;

/**
 * 责任免赔相关枚举
 */
@Getter
public enum CartesianFactorEnum {

    /**
     * NONE
     */
    NONE("NONE", "否"),

    /**
     * 不同免赔区分因素
     */
    SOCIAL_SECURITY("SOCIAL_SECURITY", "区分是否社保"),

    HOSPITAL_LEVEL("HOSPITAL_LEVEL", "区分医院等级"),

    HOSPITAL_TYPE("HOSPITAL_TYPE", "区分医院性质"),

    LIABILITY_FEE("LIABILITY_FEE", "区分承担费用类型"),

//    PERSONAL_AMOUNT("PERSONAL_AMOUNT", "个人赔付额度"),

    /**
     * 赔付比例相关区分因素
     */
    MEDICAL_INSURANCE("MEDICAL_INSURANCE", "区分是否医保"),

//    HOSPITAL_LEVEL("HOSPITAL_LEVEL", "区分医院等级"),

//    HOSPITAL_TYPE("HOSPITAL_TYPE", "区分医院性质"),

    SEVERENESS("SEVERENESS", "区分程度比例"),

    AGE("AGE", "区分年龄区间"),

    TOTAL_FEE("TOTAL_FEE", "发票总费用区间"),

    ADJUSTMENT_FEE("ADJUSTMENT_FEE", "案件理算金额区间"),

    CLAIM_ACCUM("CLAIM_ACCUM", "赔案累计区间"),

    PAY_TIMES("PAY_TIMES", "区分赔付次数"),

    ;

    private final String code;
    private final String value;

    CartesianFactorEnum(String code, String value) {
        this.code = code;
        this.value = value;
    }

    public static CartesianFactorEnum getByCode(String code) {
        for (CartesianFactorEnum e : CartesianFactorEnum.values()) {
            if (e.getCode().equals(code)) {
                return e;
            }
        }
        return null;
    }
}
