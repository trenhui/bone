package com.bone.tpa.api.enums;

import lombok.Getter;

@Getter
public enum SignSystemSourceEnum {
    SAAS(0,"saas"),
    TPA(1,"tpa"),
    ;
    private Integer code;
    private String desc;

    SignSystemSourceEnum(Integer code, String desc) {
        this.code = code;
        this.desc = desc;
    }

}
