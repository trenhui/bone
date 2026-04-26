package com.bone.tpa.sdk.adjustment.enums;

import lombok.Getter;

/**
 * 责任类型枚举
 */
@Getter
public enum LiabilityTypeEnum {

    REIMBURSEMENT("REIMBURSEMENT", "费用报销型", 0),

    FIXED_AMOUNT("FIXED_AMOUNT", "定额给付型", 2),

    ALLOWANCE("ALLOWANCE", "津贴给付型", 1),

            ;

    private final String code;
    private final String value;
    private final int pushValue;

    LiabilityTypeEnum(String code, String value, int pushValue) {
        this.code = code;
        this.value = value;
        this.pushValue = pushValue;
    }

    public static LiabilityTypeEnum getByCode(String code) {
        for (LiabilityTypeEnum e : LiabilityTypeEnum.values()) {
            if (e.getCode().equals(code)) {
                return e;
            }
        }
        return null;
    }
}
