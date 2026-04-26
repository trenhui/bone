package com.bone.lowcode.infra.application.vo.submitRule;

import lombok.Data;

import java.util.Date;
import java.util.List;

@Data
public class GetSubmitRuleByPageVO {

    /**
     * 主键
     */
    private String id;

    /**
     * 字段列表
     */
    private List<FieldOfSubmitRule> fieldList;

    /**
     * 函数类型
     */
    private String functionType;

    /**
     * 函数名
     */
    private String functionName;

    /**
     * 操作符
     */
    private String operator;

    /**
     * 值类型
     */
    private String valueType;

    /**
     * 固定值文本或动态字段id
     */
    private Object value;

    /**
     * 0：未启用，1：已启用
     */
    private Byte status;

    /**
     * 校验方式，0：强校验，阻止作业流程，1：仅提示，可跳过继续作业
     */
    private Byte verifyType;

    /**
     * 所属页面id
     */
    private String pageId;

    /**
     * 0:通用规则 1:专属规则
     */
    private Byte genericOrExclusive;

    /**
     * 提交时不符规则的错误提示文案
     */
    private String errorPrompt;

    /**
     * 创建人
     */
    private Long createBy;

    /**
     * 创建时间
     */
    private Date createTime;
}
