package com.bone.lowcode.infra.application.vo.optionSet;

import lombok.Data;

@Data
public class QueryCollectionBindVO {

    /**
     * 主体code
     */
    private String bizIdentityCode;

    /**
     * 所属modelCode
     */
    private String modeCode;
    /**
     * 字段code
     */
    private String fieldCode;

    /**
     * 选项集 or  主数据的枚举code
     */
    private Byte bindType;

    /**
     * 绑定目标
     * 如果是选项集，则返回选项集的code（绑定哪个选项集）
     * 如果是主数据，则返回主数据数据类型的枚举code（省，市）
     */
    private String bindTarget;


    private QueryCollectionByOptionCnVO defaultValue;
}
