package com.bone.tpa.sdk.adjustment.enums;

import lombok.Getter;

/**
 * 给付依据（定额给付使用
 */
public enum PaymentBasisEnum {

    SEVERE("SEVERE", "重疾标识"),

    DISEASE("DISEASE", "疾病种类"),

    TOTAL_FEE("TOTAL_FEE", "发票总费用"),

    DISABILITY("DISABILITY", "失能标识"),
    ;

    private final String code;
    private final String description;

    PaymentBasisEnum(String code, String description) {
        this.code = code;
        this.description = description;
    }

    public static PaymentBasisEnum getByCode(String code) {
        for (PaymentBasisEnum item : PaymentBasisEnum.values()) {
            if (item.getCode().equals(code)) {
                return item;
            }
        }
        return null;
    }
}
