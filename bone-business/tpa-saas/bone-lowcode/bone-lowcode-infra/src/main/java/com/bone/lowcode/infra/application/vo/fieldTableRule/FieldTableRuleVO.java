package com.bone.lowcode.infra.application.vo.fieldTableRule;

import com.bone.lowcode.infra.application.vo.simple.Field;
import com.bone.lowcode.infra.application.vo.simple.Table;
import lombok.Data;

import java.util.Date;

@Data
public class FieldTableRuleVO {

    /**
     * 主键
     */
    private String id;

    /**
     * 当前字段
     */
    private Field currentField;

    /**
     * 当前字段操作符
     */
    private String sourceOperator;

    /**
     * 0:固定值 1:动态值
     */
    private String sourceValueType;

    /**
     * 固定值文本或动态字段
     */
    private Object sourceValue;

    /**
     * 固定值是选项值code时的选项值名称
     */
    private String sourceValueCn;

    /**
     * 目标表格
     */
    private Table targetTable;

    /**
     * 目标表格属性名
     */
    private String attributeName;

    /**
     * 目标表格属性值
     */
    private String attributeValue;

    /**
     * 启用状态，0：不启用，1：启用
     */
    private Byte status;

    /**
     * 创建人
     */
    private String createBy;

    /**
     * 创建时间
     */
    private Date createTime;

    /**
     * 修改人
     */
    private String updateBy;

    /**
     * 修改时间
     */
    private Date updateTime;

    /**
     * 0:通用规则 1:专属规则
     */
    private Byte genericOrExclusive;

    /**
     * 业务字段所属模型名称
     */
    private String modelName;
}
