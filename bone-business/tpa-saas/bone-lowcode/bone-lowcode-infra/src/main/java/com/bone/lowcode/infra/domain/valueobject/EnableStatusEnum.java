package com.bone.lowcode.infra.domain.valueobject;

import lombok.Getter;

//开启状态枚举
@Getter
public enum EnableStatusEnum {
    ENABLE((byte) 1, "开启"),
    UN_ENABLE((byte) 0, "不开启");
    private final byte code;
    private final String desc;

    EnableStatusEnum(byte code, String desc) {
        this.code = code;
        this.desc = desc;
    }
}
