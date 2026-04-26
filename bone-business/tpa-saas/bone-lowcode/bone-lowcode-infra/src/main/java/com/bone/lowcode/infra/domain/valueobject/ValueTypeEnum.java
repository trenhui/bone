package com.bone.lowcode.infra.domain.valueobject;

import lombok.Getter;

//规则中的值类型枚举
@Getter
public enum ValueTypeEnum {
    FIXED("fixed", "固定值"),
    DYNAMIC("dynamic", "动态值");

    private final String value;
    private final String desc;

    ValueTypeEnum(String value, String desc) {
        this.value = value;
        this.desc = desc;
    }
}
