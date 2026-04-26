package com.bone.tpa.api.enums;

import lombok.Getter;

@Getter
public enum InvoiceMedicalTypeEnum {
    Ptsj("1","普通收据"),
    Ybsp("2","医保审批表"),
    Ybts("3","医保特殊病"),
    Fgd("4","分割单");
    ;
    private String code ;
    private String desc;
    InvoiceMedicalTypeEnum(String code, String desc) {
        this.code = code;
        this.desc = desc;
    }

    public static InvoiceMedicalTypeEnum getByCode(String code) {
        for (InvoiceMedicalTypeEnum invoiceYbType : InvoiceMedicalTypeEnum.values()) {
            if (invoiceYbType.getCode().equals(code)) {
                return invoiceYbType;
            }
        }
        return null;
    }
}
