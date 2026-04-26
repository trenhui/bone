package com.bone.lowcode.infra.domain.valueobject;

import lombok.Getter;

//选项集节点类型枚举
@Getter
public enum OptionSetNodeEnum {
    ROOT_NODE((byte) 1, "根节点"),
    INTERMEDIATE_NODE((byte) 2, "中间节点"),
    LEAF_NODE((byte) 3, "叶子节点");

    private final byte code;
    private final String desc;

    OptionSetNodeEnum(byte code, String desc) {
        this.code = code;
        this.desc = desc;
    }
}
