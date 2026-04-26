package com.bone.lowcode.infra.domain.valueobject;

import lombok.Getter;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

//字段联动展示规则类型枚举
@Getter
public enum LinkedDisplayRuleTypeEnum {

    EXTRA_PROPERTY((byte) 0, "扩展属性名"),
    SCRIPT((byte) 1, "后置脚本");

    private final byte code;
    private final String desc;

    LinkedDisplayRuleTypeEnum(byte code, String desc) {
        this.code = code;
        this.desc = desc;
    }

    public static Set<Byte> getAllCode() {
        return Arrays.stream(values()).map(LinkedDisplayRuleTypeEnum::getCode).collect(Collectors.toSet());
    }
}
