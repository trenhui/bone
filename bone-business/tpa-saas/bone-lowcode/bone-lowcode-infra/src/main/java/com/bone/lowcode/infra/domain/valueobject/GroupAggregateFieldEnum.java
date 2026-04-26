package com.bone.lowcode.infra.domain.valueobject;

import lombok.Getter;

@Getter
public enum GroupAggregateFieldEnum {

    group((byte) 1, "分组字段"),
    Aggregate((byte) 2, "聚合字段"),
    other((byte) 3, "其它展示字段"),
    ;
    private final byte code;
    private final String desc;

    GroupAggregateFieldEnum(byte code, String desc) {
        this.code = code;
        this.desc = desc;
    }
}
