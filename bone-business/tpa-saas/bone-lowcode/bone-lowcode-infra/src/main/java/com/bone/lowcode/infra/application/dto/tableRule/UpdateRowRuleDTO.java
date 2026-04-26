package com.bone.lowcode.infra.application.dto.tableRule;

import lombok.Data;

import java.util.List;

@Data
public class UpdateRowRuleDTO {
    /**
     * 规则id
     */
    private Long id;

    /**
     * 源字段id列表
     */
    private List<Long> sourceFieldIdList;

    /**
     * 函数名
     */
    private String functionName;

    /**
     * 目标字段id
     */
    private Long targetFieldId;

    /**
     * 提交时不符规则的错误提示文案
     */
    private String errorPrompt;

    /**
     * 启用状态，0：不启用，1：启用
     */
    private Byte status;

    /**
     * 校验方式，0：强校验，阻止作业流程，1：仅提示，可跳过继续作业
     */
    private Byte verifyType;
}
