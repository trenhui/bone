package com.bone.tools.codegen.domain.entity;

import com.bone.core.annotation.Id;
import com.bone.metadata.sdk.domain.annotation.Table;
import com.baomidou.mybatisplus.generator.config.po.TableField;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

/**
 * 代码生成表字段配置 领域实体
 * <p>
 * 表示代码生成功能中的表字段配置信息，是代码生成领域的重要组成部分
 *
 * @author 芋道源码
 */
@Table("infra_codegen_column")
@Data
@Accessors(chain = true)
@EqualsAndHashCode(callSuper = true)
public class CodegenColumnDO extends BaseDO<Long> {

    /**
     * 字段配置ID
     * <p>
     * 唯一标识一个代码生成字段配置
     */
    @Id
    private Long id;
    
    @Override
    public Long getId() {
        return id;
    }
    
    @Override
    public void setId(Long id) {
        this.id = id;
    }

    /**
     * 表配置ID
     * <p>
     * 关联CodegenTableDO实体的主键ID，表示该字段所属的表配置
     */
    private Long tableId;

    // ========== 数据库字段相关属性 ==========

    /**
     * 字段名
     * <p>
     * 对应数据库中的实际字段名称
     */
    private String columnName;
    
    /**
     * 数据库字段类型
     * <p>
     * 如：varchar、int、datetime等
     */
    private String dataType;
    
    /**
     * 字段描述
     * <p>
     * 数据库字段的注释描述
     */
    private String columnComment;
    
    /**
     * 是否允许为空
     * <p>
     * true: 允许为空
     * false: 不允许为空
     */
    private Boolean nullable;
    
    /**
     * 是否主键
     * <p>
     * true: 主键字段
     * false: 非主键字段
     */
    private Boolean primaryKey;
    
    /**
     * 字段排序位置
     * <p>
     * 表示字段在表中的顺序位置，影响生成代码中的字段顺序
     */
    private Integer ordinalPosition;

    // ========== Java属性相关配置 ==========

    /**
     * Java属性类型
     * <p>
     * 如：String、Boolean、Integer等
     */
    private String javaType;
    
    /**
     * Java属性名
     * <p>
     * 驼峰命名的属性名，如：userName、createTime等
     */
    private String javaField;
    
    /**
     * 字典类型
     * <p>
     * 关联字典表的类型编码，表示字段使用的字典，用于前端下拉选择等场景
     */
    private String dictType;
    
    /**
     * 数据示例
     * <p>
     * 用于生成Swagger注解的example属性
     */
    private String example;

    // ========== 业务操作相关配置 ==========

    /**
     * 是否用于创建操作
     * <p>
     * true: 在创建表单中显示此字段
     * false: 不在创建表单中显示此字段
     */
    private Boolean createOperation;
    
    /**
     * 是否用于更新操作
     * <p>
     * true: 在更新表单中显示此字段
     * false: 不在更新表单中显示此字段
     */
    private Boolean updateOperation;
    
    /**
     * 是否用于查询操作
     * <p>
     * true: 在查询条件中显示此字段
     * false: 不在查询条件中显示此字段
     */
    private Boolean listOperation;
    
    /**
     * 查询条件类型
     * <p>
     * 表示在查询时使用的条件类型，如：等于、大于、模糊匹配等，决定生成查询条件的SQL片段
     */
    private String listOperationCondition;
    
    /**
     * 是否在查询结果中返回
     * <p>
     * true: 在查询结果列表中显示此字段
     * false: 不在查询结果列表中显示此字段
     */
    private Boolean listOperationResult;

    // ========== 界面展示相关配置 ==========

    /**
     * HTML展示类型
     * <p>
     * 表示在界面上使用的控件类型，如：input、select、radio等，决定前端表单和列表的展示形式
     */
    private String htmlType;

}
