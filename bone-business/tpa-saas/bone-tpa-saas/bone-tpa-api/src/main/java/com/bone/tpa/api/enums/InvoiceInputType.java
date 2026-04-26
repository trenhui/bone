package com.bone.tpa.api.enums;

import lombok.Getter;

//自动化、外包商、录入组
@Getter
public enum InvoiceInputType {
    自动化("auto", "自动化"),
    外包商("outsourcing", "外包商"),
    录入组("input", "录入组")
    ;
    private String code ;
    private String desc;

    InvoiceInputType(String code, String desc) {
        this.code = code;
        this.desc = desc;
    }
}
