package com.bone.tpa.api.enums;

import lombok.Getter;

@Getter
public enum SlipAttribute {
    //0：未设置属性（默认）1：基金业务；2：团险业务
    未设置属性("0","未设置属性（默认）"),
    基金业务("1","基金业务"),
    团险业务("2","团险业务")
    ;
    private String code ;
    private String desc;

    SlipAttribute(String code, String desc) {
        this.code = code;
        this.desc = desc;
    }

    public static SlipAttribute getByCode(String code) {
        for (SlipAttribute slipAttribute : SlipAttribute.values()) {
            if (slipAttribute.getCode().equals(code)) {
                return slipAttribute;
            }
        }
        return null;
    }
}
