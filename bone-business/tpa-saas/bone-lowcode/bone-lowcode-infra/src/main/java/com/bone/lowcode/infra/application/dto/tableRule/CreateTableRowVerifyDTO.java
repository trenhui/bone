package com.bone.lowcode.infra.application.dto.tableRule;

import lombok.Data;

import java.util.List;

@Data
public class CreateTableRowVerifyDTO {

    /**
     * 所属表格id
     */
    private Long tableId;

    /**
     * 字段id列表
     */
    private List<Long> fieldIdList;

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
    private String value;

    /**
     * 校验方式，0：强校验，阻止作业流程，1：仅提示，可跳过继续作业
     */
    private Byte verifyType;
}
