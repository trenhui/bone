package com.bone.tpa.sdk.claim.enums;

public enum CfgAutoTypeEnum {
    AUTO(1, "自动"),
    MANUAL(2, "非自动化"),
    ;
    private Integer code ;

    private String desc;

    CfgAutoTypeEnum(Integer code, String desc) {
        this.code = code;
        this.desc = desc;
    }
    public static CfgAutoTypeEnum getByCode(Integer code) {
        for (CfgAutoTypeEnum cfgAutoTypeEnum : CfgAutoTypeEnum.values()) {
            if (cfgAutoTypeEnum.getCode().equals(code)) {
                return cfgAutoTypeEnum;
            }
        }
        return null;
    }

    public Integer getCode() {
        return code;
    }

    public String getDesc() {
        return desc;
    }
}
