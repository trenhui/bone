package com.bone.lowcode.infra.domain.valueobject;

import lombok.Getter;

//block类型枚举
@Getter
public enum BlockEnum {

    CLAIM_INFO("赔案信息", "MainBlock", "赔案信息块"),
    OTHER_BLOCK("", "Block", "其它块"),
    ;
    private final String name;
    private final String type;
    private final String desc;

    BlockEnum(String name, String type, String desc) {
        this.name = name;
        this.type = type;
        this.desc = desc;
    }
}
