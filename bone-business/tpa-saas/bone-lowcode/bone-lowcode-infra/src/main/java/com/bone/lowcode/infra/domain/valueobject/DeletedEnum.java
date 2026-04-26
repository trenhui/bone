package com.bone.lowcode.infra.domain.valueobject;

import lombok.Getter;

//删除方式枚举
@Getter
public enum DeletedEnum {
    DELETED((byte) 1, "是"),
    UNDELETED((byte) 0, "否");
    private final byte code;
    private final String desc;

    DeletedEnum(byte code, String desc) {
        this.code = code;
        this.desc = desc;
    }
}
