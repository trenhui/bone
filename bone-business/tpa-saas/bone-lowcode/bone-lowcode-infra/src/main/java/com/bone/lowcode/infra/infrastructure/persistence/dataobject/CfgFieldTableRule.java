package com.bone.lowcode.infra.infrastructure.persistence.dataobject;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.util.Date;

@Data
@TableName("cfg_field_table_rule")
public class CfgFieldTableRule implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 主键
     */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * 所属页面id（冗余字段）
     */
    private Long pageId;

    /**
     * 源头规则id
     */
    private Long sourceId;

    /**
     * 当前字段id
     */
    private Long fieldId;

    /**
     * 当前字段操作符
     */
    private String sourceOperator;

    /**
     * 0:固定值 1:动态值
     */
    private String sourceValueType;

    /**
     * 固定值文本或动态字段id
     */
    private String sourceValue;

    /**
     * 固定值是选项值code时的选项值名称
     */
    private String sourceValueCn;

    /**
     * 目标表格id
     */
    private Long tableId;

    /**
     * 目标表格属性名
     */
    private String attributeName;

    /**
     * 目标表格属性值
     */
    private String attributeValue;

    /**
     * 0:通用规则 1:专属规则
     */
    private Byte genericOrExclusive;

    /**
     * 启用状态，0：不启用，1：启用
     */
    private Byte status;

    /**
     * 是否删除，0：未删，1：已删
     */
    private Byte deleted;

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
}
