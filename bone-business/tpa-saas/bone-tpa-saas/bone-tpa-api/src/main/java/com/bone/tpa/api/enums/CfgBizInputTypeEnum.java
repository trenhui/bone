package com.bone.tpa.api.enums;

import lombok.Getter;

@Getter
public enum CfgBizInputTypeEnum {
    INVOICE(1, "发票层"),
    INVOICE_AND_PROJECT(2, "发票层和项目层"),

    ;
    private Integer code ;
    private String desc ;

    CfgBizInputTypeEnum(Integer code, String desc) {
        this.code = code;
        this.desc = desc;
    }

    public static CfgBizInputTypeEnum getByCode(Integer code) {
        for (CfgBizInputTypeEnum type : CfgBizInputTypeEnum.values()) {
            if (type.getCode().equals(code)) {
                return type;
            }
        }
        return null;
    }
}
