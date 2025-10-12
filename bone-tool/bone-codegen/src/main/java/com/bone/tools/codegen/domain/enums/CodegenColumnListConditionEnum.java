package com.bone.tools.codegen.domain.enums;

import lombok.Getter;

/**
 * 代码生成器的字段过滤条件枚举
 */
@Getter
public enum CodegenColumnListConditionEnum {

    EQ("="),
    NE("!="),
    GT(">"),
    GTE(">="),
    LT("<"),
    LTE("<="),
    LIKE("LIKE"),
    BETWEEN("BETWEEN");

    /**
     * 条件
     */
    private final String condition;
    
    // 私有构造函数
    private CodegenColumnListConditionEnum(String condition) {
        this.condition = condition;
    }

}
