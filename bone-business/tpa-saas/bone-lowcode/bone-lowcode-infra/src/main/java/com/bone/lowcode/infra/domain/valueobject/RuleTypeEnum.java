package com.bone.lowcode.infra.domain.valueobject;

import lombok.AllArgsConstructor;
import lombok.Getter;

//规则类型枚举
@AllArgsConstructor
@Getter
public enum RuleTypeEnum {

    GENERIC((byte) 0, "通用规则"),
    EXCLUSIVE((byte) 1, "专属规则"),
    ;


    private final byte code;
    private final String desc;
}
