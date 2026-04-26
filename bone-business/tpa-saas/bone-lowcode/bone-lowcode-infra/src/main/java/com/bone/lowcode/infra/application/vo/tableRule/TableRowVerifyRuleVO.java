package com.bone.lowcode.infra.application.vo.tableRule;

import com.bone.lowcode.infra.application.vo.table.FieldSimpleInfo;
import lombok.Data;

import java.util.List;

@Data
public class TableRowVerifyRuleVO {

    private String id;

    private List<FieldSimpleInfo> fieldList;

    private String functionName;

    private String operator;

    private String valueType;

    private Object value;

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

    private String tableId;

    private String tableName;
}
