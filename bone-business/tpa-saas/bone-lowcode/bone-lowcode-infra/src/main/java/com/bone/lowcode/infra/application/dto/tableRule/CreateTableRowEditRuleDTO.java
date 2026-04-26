package com.bone.lowcode.infra.application.dto.tableRule;

import lombok.Data;

import java.util.List;

@Data
public class CreateTableRowEditRuleDTO {
    /**
     * 所属表格id
     */
    private Long tableId;

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
     * 校验方式，0：强校验，阻止作业流程，1：仅提示，可跳过继续作业
     */
    private Byte verifyType;
}
