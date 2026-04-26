package com.bone.tpa.api.enums;

import lombok.Getter;

@Getter
public enum InvoiceYbType {
    YBPay("1","城镇居民" ),
    SBPay("2","城镇职工" ),
    ThirdPay("3","新农合"),
    FOURPay("4","无"),


    ;
    private String code ;
    private String desc;
    InvoiceYbType(String code, String desc) {
        this.code = code;
        this.desc = desc;
    }

    public static InvoiceYbType getInvoiceYbType(String code) {
        for (InvoiceYbType invoiceYbType : InvoiceYbType.values()) {
            if (invoiceYbType.getCode().equals(code)) {
                return invoiceYbType;
            }
        }
        return null;
    }
}
