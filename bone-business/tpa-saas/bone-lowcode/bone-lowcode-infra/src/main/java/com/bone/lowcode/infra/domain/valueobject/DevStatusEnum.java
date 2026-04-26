package com.bone.lowcode.infra.domain.valueobject;

import lombok.Getter;

//开发状态枚举
@Getter
public enum DevStatusEnum {
    UN_COMPLETE((byte) 0, "开发未完成"),
    COMPLETE((byte) 1, "开发完成"),
    ;

    private final byte code;
    private final String desc;

    DevStatusEnum(byte code, String desc) {
        this.code = code;
        this.desc = desc;
    }
}
