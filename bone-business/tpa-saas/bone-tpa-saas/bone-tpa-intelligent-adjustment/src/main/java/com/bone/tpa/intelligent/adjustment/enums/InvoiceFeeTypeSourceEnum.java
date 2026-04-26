package com.bone.tpa.intelligent.adjustment.enums;

import lombok.Getter;

/**
 * 发票金额来源枚举
 */
@Getter
public enum InvoiceFeeTypeSourceEnum {

    INPUT("INPUT", "录入项"),

    CALCULATE("CALCULATE", "计算项"),

    ;

    private final String code;
    private final String value;

    InvoiceFeeTypeSourceEnum(String code, String value) {
        this.code = code;
        this.value = value;
    }

    public static InvoiceFeeTypeSourceEnum getByCode(String code) {
        for (InvoiceFeeTypeSourceEnum e : InvoiceFeeTypeSourceEnum.values()) {
            if (e.getCode().equals(code)) {
                return e;
            }
        }
        return null;
    }
}
