package com.bone.lowcode.infra.domain.valueobject;

import lombok.Getter;

//字段容器状态枚举
@Getter
public enum PresentationFormatEnum {
    FIELDSET(0, "区块字段"),
    TABLE(1, "表格"),
    ALL(2, "兼具"),
    ;
    private final int code;
    private final String desc;

    PresentationFormatEnum(int code, String desc) {
        this.code = code;
        this.desc = desc;
    }
}
