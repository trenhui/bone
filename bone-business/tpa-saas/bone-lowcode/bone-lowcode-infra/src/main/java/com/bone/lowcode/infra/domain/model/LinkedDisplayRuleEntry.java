package com.bone.lowcode.infra.domain.model;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class LinkedDisplayRuleEntry {

    /**
     * 0或null:扩展属性名    1:后置脚本
     */
    private Byte type=0;

    /**
     * 扩展属性名
     */
    private String extraProperty;

    /**
     * 脚本
     */
    private String script;

    /**
     * 受影响字段id
     */
    @NotNull(message = "受影响字段id不能为空")
    private Long fieldId;
}
