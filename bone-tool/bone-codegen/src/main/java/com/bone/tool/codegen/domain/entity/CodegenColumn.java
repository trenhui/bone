package com.bone.tool.codegen.domain.entity;

import com.bone.core.domain.entity.AbstractEntity;
import com.bone.metadata.sdk.domain.annotation.Table;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;
import java.io.Serializable;

/**
 * 代码生成列配置
 * 用于定义代码生成过程中数据表列的各项属性和生成规则
 */
@Table("codegen_column")
@Data
@Accessors(chain = true)
@EqualsAndHashCode(callSuper = true)
public class CodegenColumn extends AbstractEntity<Long> implements Serializable {
    private static final long serialVersionUID = 1L;

    /** 所属表ID */
    private Long tableId;
    
    /** 数据库列名 */
    private String columnName;
    
    /** 数据库数据类型 */
    private String dataType;
    
    /** 列注释 */
    private String columnComment;
    
    /** Java数据类型 */
    private String javaType;
    
    /** Java字段名 */
    private String javaField;
    
    /** 是否主键 */
    private Boolean primaryKey;
    
    /** 是否自增 */
    private Boolean autoIncrement;
    
    /** 是否可为空 */
    private Boolean nullable;
    
    /** 是否用于创建操作 */
    private Boolean enableCreate;
    
    /** 是否用于更新操作 */
    private Boolean enableUpdate;
    
    /** 是否用于列表查询 */
    private Boolean enableQuery;
    
    /** 是否在列表结果中展示 */
    private Boolean showInList;
    
    /** 列表查询条件类型 */
    private String listQueryCondition;
    
    /** HTML表单控件类型 */
    private String htmlType;
    
    /** 字典类型编码 */
    private String dictType;
    
    /** 关联表名 */
    private String relationTableName;
    
    /** 关联表展示字段 */
    private String relationShowField;
    
    /** 关联表查询字段 */
    private String relationQueryField;
    
    /** 扩展属性，JSON格式 */
    private String extraAttrs;
}
