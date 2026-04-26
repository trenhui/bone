package com.bone.lowcode.infra.application.vo.tableRule;

import com.bone.lowcode.infra.application.vo.table.FieldSimpleInfo;
import lombok.Data;

import java.util.List;

@Data
public class CrossTableDataVerifyRuleVO {

    private String id;

    /**
     * 在数据上存在一对多关系的两个表格中'多'方表格的id
     */
    private String currentTableId;

    /**
     * 在数据上存在一对多关系的两个表格中'多'方表格的Name
     */
    private String currentTableName;

    /**
     * 在数据上存在一对多关系的两个表格中'多'方表格的关系字段id
     */
    private FieldSimpleInfo currentRelationField;

    /**
     * 在数据上存在一对多关系的两个表格中一'方表格的id
     */
    private String targetTableId;

    /**
     * 在数据上存在一对多关系的两个表格中一'方表格的Name
     */
    private String targetTableName;

    /**
     * 在数据上存在一对多关系的两个表格中'一'方表格的关系字段
     */
    private FieldSimpleInfo targetRelationField;

    /**
     * '多'方表格字段列表
     */
    private List<FieldSimpleInfo> currentTableFieldList;

    /**
     * 函数名
     */
    private String functionName;

    /**
     * '一'方表格字段
     */
    private FieldSimpleInfo targetTableField;

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
