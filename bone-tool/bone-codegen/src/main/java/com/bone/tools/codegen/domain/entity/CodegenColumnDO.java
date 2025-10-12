package com.bone.tools.codegen.domain.entity;

import com.bone.core.annotation.Id;
import com.bone.metadata.sdk.domain.annotation.Table;
import com.baomidou.mybatisplus.generator.config.po.TableField;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

/**
 * 代码生成 column 字段定义
 *
 * @author 芋道源码
 */
@Table("infra_codegen_column")
@Data
@Accessors(chain = true)
@EqualsAndHashCode(callSuper = true)
public class CodegenColumnDO extends BaseDO<Long> {

    /**
     * ID 编号
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
     * 表编号
     * <p>
     * 关联 {@link CodegenTableDO#getId()}
     */
    private Long tableId;

    // ========== 表相关字段 ==========

    /**
     * 字段名
     *
     * 关联 {@link TableField#getName()}
     */
    private String columnName;
    /**
     * 数据库字段类型
     *
     * 关联 {@link TableField.MetaInfo#getJdbcType()}
     */
    private String dataType;
    /**
     * 字段描述
     *
     * 关联 {@link TableField#getComment()}
     */
    private String columnComment;
    /**
     * 是否允许为空
     *
     * 关联 {@link TableField.MetaInfo#isNullable()}
     */
    private Boolean nullable;
    /**
     * 是否主键
     *
     * 关联 {@link TableField#isKeyFlag()}
     */
    private Boolean primaryKey;
    /**
     * 排序
     */
    private Integer ordinalPosition;

    // ========== Java 相关字段 ==========

    /**
     * Java 属性类型
     *
     * 例如说 String、Boolean 等等
     *
     * 关联 {@link TableField#getColumnType()}
     */
    private String javaType;
    /**
     * Java 属性名
     *
     * 关联 {@link TableField#getPropertyName()}
     */
    private String javaField;
    /**
     * 字典类型
     * <p>
     * 关联 DictTypeDO 的 type 属性
     */
    private String dictType;
    /**
     * 数据示例，主要用于生成 Swagger 注解的 example 字段
     */
    private String example;

    // ========== CRUD 相关字段 ==========

    /**
     * 是否为 Create 创建操作的字段
     */
    private Boolean createOperation;
    /**
     * 是否为 Update 更新操作的字段
     */
    private Boolean updateOperation;
    /**
     * 是否为 List 查询操作的字段
     */
    private Boolean listOperation;
    /**
     * List 查询操作的条件类型
     * <p>
     * 枚举 {@link CodegenColumnListConditionEnum}
     */
    private String listOperationCondition;
    /**
     * 是否为 List 查询操作的返回字段
     */
    private Boolean listOperationResult;

    // ========== UI 相关字段 ==========

    /**
     * 显示类型
     * <p>
     * 枚举 {@link CodegenColumnHtmlTypeEnum}
     */
    private String htmlType;

}
