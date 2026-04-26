package com.bone.lowcode.infra.application.dto.fieldTableRule;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class UpdateFieldTableRuleDTO {

    @NotNull(message = "数据id不能为空")
    private Long id;

    /**
     * 当前字段操作符
     */
//    @NotBlank(message = "当前字段操作符不能为空")
    private String sourceOperator;

    /**
     * 0:固定值 1:动态值
     */
    private String sourceValueType;

    /**
     * 固定值文本或动态字段id
     */
    private String sourceValue;

    /**
     * 固定值是选项值code时的选项值名称
     */
    private String sourceValueCn;

    /**
     * 目标表格id
     */
//    @NotNull(message = "目标表格id不能为空")
    private Long tableId;

    /**
     * 目标表格属性名
     */
//    @NotBlank(message = "目标表格属性名不能为空")
    private String attributeName;

    /**
     * 目标表格属性值
     */
//    @NotBlank(message = "目标表格属性值不能为空")
    private String attributeValue;

    /**
     * 启用状态，0：不启用，1：启用
     */
    private Byte status;
}
