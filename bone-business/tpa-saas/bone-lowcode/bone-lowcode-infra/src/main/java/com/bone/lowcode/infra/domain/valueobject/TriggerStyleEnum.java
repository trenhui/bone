package com.bone.lowcode.infra.domain.valueobject;

import lombok.Getter;

//事件触发器类型枚举
@Getter
public enum TriggerStyleEnum {
    BUTTON((byte) 0, "button"),
    LINK((byte) 1, "link"),

    ;
    private final byte code;
    private final String desc;

    TriggerStyleEnum(byte code, String desc) {
        this.code = code;
        this.desc = desc;
    }
}
