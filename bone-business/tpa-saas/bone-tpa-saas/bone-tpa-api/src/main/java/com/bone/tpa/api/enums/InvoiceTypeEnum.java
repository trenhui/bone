package com.bone.tpa.api.enums;

import lombok.Getter;

/**
 *   {
 *           label: '门诊',
 *           value: "1"
 *         },
 *         {
 *           label: '住院',
 *           value: "2"
 *         },
 *         {
 *           label: '购药',
 *           value: "3"
 *         }
 */
@Getter
public enum InvoiceTypeEnum {
    门诊("1", "门诊"),
    住院("2", "住院"),
    购药("3", "购药")
    ;
    private String code;
    private String desc;

    InvoiceTypeEnum(String code, String desc) {
        this.code = code;
        this.desc = desc;
    }

    public static InvoiceTypeEnum getByCode(String code) {
        for (InvoiceTypeEnum invoiceTypeEnum : InvoiceTypeEnum.values()) {
            if (invoiceTypeEnum.getCode().equals(code)) {
                return invoiceTypeEnum;
            }
        }
        return null;

    }

    public static InvoiceTypeEnum getByDesc(String desc) {
        for (InvoiceTypeEnum invoiceTypeEnum : InvoiceTypeEnum.values()) {
            if (invoiceTypeEnum.getDesc().equals(desc)) {
                return invoiceTypeEnum;
            }
        }
        return null;

    }
}
