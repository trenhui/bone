package com.bone.lowcode.infra.application.dto.tableRule;

import lombok.Data;

import java.util.List;

@Data
public class UpdateCrossTableDataVerifyRuleDTO {

    private Long id;

    /**
     * 在数据上存在一对多关系的两个表格中'多'方表格的id
     */
    private Long currentTableId;

    /**
     * 在数据上存在一对多关系的两个表格中一'方表格的id
     */
    private Long targetTableId;


    /**
     * '多'方表格字段id列表
     */
    private List<Long> currentTableFieldIdList;

    /**
     * 函数名
     */
    private String functionName;

    /**
     * '一'方表格字段id
     */
    private Long targetTableFieldId;

    /**
     * 操作符
     */
    private String operator;

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
