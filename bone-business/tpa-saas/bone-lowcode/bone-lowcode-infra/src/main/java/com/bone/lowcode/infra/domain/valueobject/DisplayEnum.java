package com.bone.lowcode.infra.domain.valueobject;

import lombok.Getter;

//展示状态枚举
@Getter
public enum DisplayEnum {
    DISPLAY((byte) 1, "展示"),
    UN_DISPLAY((byte)0, "隐藏");
    private final byte code;
    private final String desc;

    DisplayEnum(byte code, String desc) {
        this.code = code;
        this.desc = desc;
    }
}
