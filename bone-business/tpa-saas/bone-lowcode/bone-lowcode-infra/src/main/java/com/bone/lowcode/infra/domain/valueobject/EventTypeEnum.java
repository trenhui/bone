package com.bone.lowcode.infra.domain.valueobject;

import lombok.Getter;

//事件类型枚举
@Getter
public enum EventTypeEnum {
    PROCESS((byte) 0, "流程事件"),
    BUSINESS((byte)1, "业务事件");
    private final byte code;
    private final String desc;

    EventTypeEnum(byte code, String desc) {
        this.code = code;
        this.desc = desc;
    }
}
