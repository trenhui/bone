package com.bone.lowcode.infra.application.dto.fieldLinkageRule;

import lombok.Data;

import java.util.List;

@Data
public class CreateFieldLinkageRuleDTO {

    /**
     * 当前字段id
     */
    private Long fieldId;

    /**
     * 当前字段操作符
     */
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
     * 目标字段id列表
     */
    private List<Long> targetFields;

    /**
     * 目标字段选属性还是值，0:属性,1:值
     */
    private String propertyOrValue;

    /**
     * 目标组件属性名
     */
    private String targetFieldPropertyName;

    /**
     * 属性选项
     */
    private Byte targetFieldPropertyValue;

    /**
     * 目标字段的值的操作符
     */
    private String targetOperator;

    /**
     * 目标字段处，0:固定值 1:动态值
     */
    private String targetValueType;

    /**
     * 目标字段处，固定值文本或动态字段id
     */
    private String targetValue;

    /**
     * 目标字段处,固定值是选项值code时的选项值名称
     */
    private String targetValueCn;

    /**
     * 校验方式，0：强校验，阻止作业流程，1：仅提示，可跳过继续作业
     */
    private Byte verifyType;
}
