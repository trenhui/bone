package com.bone.lowcode.infra.application.vo.optionSet;

import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class FieldLinkedDisplayRuleVO {

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
    private String fieldId;

    /**
     * 受影响字段名称
     */
    private String bizName;

    /**
     * 受影响字段编码
     */
    private String bizCode;

    /**
     * 字段取值表达式
     */
    private String dataBinding;

    public FieldLinkedDisplayRuleVO(String extraProperty) {
        this.extraProperty = extraProperty;
    }

    public  FieldLinkedDisplayRuleVO(Byte type, String script) {
        this.type = type;
        this.script = script;
    }
}
