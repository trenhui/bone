package com.bone.lowcode.infra.domain.valueobject;

import lombok.Getter;

//搜索时匹配方式枚举
@Getter
public enum MatchModeEnum {
    PRECISE((byte) 1, "精准匹配"),
    FUZZY((byte) 2, "模糊匹配"),
    PREFIX((byte) 3, "前缀匹配");
    private final byte code;
    private final String desc;

    MatchModeEnum(byte code, String desc) {
        this.code = code;
        this.desc = desc;
    }
}
