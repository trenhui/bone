package com.bone.tpa.api.enums;


import lombok.Getter;

@Getter
public enum InputTypeConfig {
    ;
    private String code ;
    private String desc;

    InputTypeConfig(String code, String desc) {
        this.code = code;
        this.desc = desc;
    }

}
