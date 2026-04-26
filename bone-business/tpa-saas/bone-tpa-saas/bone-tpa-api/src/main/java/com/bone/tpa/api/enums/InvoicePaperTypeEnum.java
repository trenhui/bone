package com.bone.tpa.api.enums;

import lombok.Getter;

@Getter
public enum InvoicePaperTypeEnum {
    PAPER("00","纸质"),
    ELECTRONIC("01","电子"),
    ;

    private String code ;

    private String desc;

    InvoicePaperTypeEnum(String code, String desc) {
        this.code = code;
        this.desc = desc;
    }


    public static InvoicePaperTypeEnum getInvoicePaperTypeEnum(String code) {
        for (InvoicePaperTypeEnum invoicePaperTypeEnum : InvoicePaperTypeEnum.values()) {
            if (invoicePaperTypeEnum.getCode().equals(code)) {
                return invoicePaperTypeEnum;
            }
        }
        return null;
    }
}
