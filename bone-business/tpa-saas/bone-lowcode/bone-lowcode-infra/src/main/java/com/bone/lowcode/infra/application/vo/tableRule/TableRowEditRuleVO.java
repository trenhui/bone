package com.bone.lowcode.infra.application.vo.tableRule;

import com.bone.lowcode.infra.application.vo.Sequence;
import com.bone.lowcode.infra.application.vo.table.RowRuleField;
import lombok.Data;

import java.util.List;

@Data
public class TableRowEditRuleVO implements Sequence {

    private String id;

    private String tableId;

    private List<RowRuleField> sourceFieldList;

    private String functionName;

    private RowRuleField targetField;

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

    /**
     * 表格名
     */
    private String tableName;

    /**
     * 序号
     */
    private Integer sequence;

    @Override
    public Integer returnSequence() {
        return sequence;
    }
}
