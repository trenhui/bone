package com.bone.lowcode.infra.domain.valueobject;

import lombok.Getter;

//状态枚举
@Getter
public enum StatusEnum {

    YES((byte) 1, "是"),
    NO((byte) 0, "否");

    private final byte code;
    private final String desc;

    StatusEnum(byte code, String desc) {
        this.code = code;
        this.desc = desc;
    }
}
