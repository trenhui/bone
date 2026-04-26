package com.bone.lowcode.infra.application.dto.tableRule;

import lombok.Data;

import java.util.List;

@Data
public class TableDataRelationDTO {
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
     * '一'方表格字段id
     */
    private Long targetTableFieldId;

    /**
     * 函数名
     */
    private String functionName;

    /**
     * 校验方式，0：强校验，阻止作业流程，1：仅提示，可跳过继续作业
     */
    private Byte verifyType;
}
