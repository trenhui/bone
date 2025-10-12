package com.bone.tools.codegen.domain.entity;

import com.bone.core.annotation.Id;
import com.bone.metadata.sdk.domain.annotation.Table;
import com.baomidou.mybatisplus.generator.config.po.TableInfo;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

/**
 * 代码生成 table 表定义
 *
 * @author 芋道源码
 */
@Table("infra_codegen_table")
@Data
@Accessors(chain = true)
@EqualsAndHashCode(callSuper = true)
public class CodegenTableDO extends BaseDO<Long> {

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
     * 数据源编号
     *
     * 关联
     */
    private Long dataSourceConfigId;
    /**
     * 生成场景
     *
     * 枚举
     */
    private Integer scene;

    // ========== 表相关字段 ==========

    /**
     * 表名称
     *
     * 关联 {@link TableInfo#getName()}
     */
    private String tableName;
    /**
     * 表描述
     *
     * 关联 {@link TableInfo#getComment()}
     */
    private String tableComment;
    /**
     * 备注
     */
    private String remark;

    // ========== 类相关字段 ==========

    /**
     * 模块名，即一级目录
     *
     * 例如说，system、infra、tool 等等
     */
    private String moduleName;
    /**
     * 包名
     *
     * 例如说，bone 等等
     */
    private String packgeName;
    /**
     * 业务名，即二级目录
     *
     * 例如说，user、permission、dict 等等
     */
    private String businessName;
    /**
     * 类名称（首字母大写）
     *
     * 例如说，SysUser、SysMenu、SysDictData 等等
     */
    private String className;
    /**
     * 类描述
     */
    private String classComment;
    /**
     * 作者
     */
    private String author;

    // ========== 生成相关字段 ==========

    private Integer templateType;

    // ========== 菜单相关字段 ==========

    /**
     * 父菜单编号
     *
     * 关联 MenuDO 的 id 属性
     */
    private Long parentMenuId;

    // ========== 主子表相关字段 ==========

    /**
     * 主表的编号
     *
     * 关联 {@link CodegenTableDO#getId()}
     */
    private Long masterTableId;
    /**
     * 【自己】子表关联主表的字段编号
     *
     * 关联 {@link CodegenColumnDO#getId()}
     */
    private Long subJoinColumnId;
    /**
     * 主表与子表是否一对多
     *
     * true：一对多
     * false：一对一
     */
    private Boolean subJoinMany;

    // ========== 树表相关字段 ==========

    /**
     * 树表的父字段编号
     *
     * 关联 {@link CodegenColumnDO#getId()}
     */
    private Long treeParentColumnId;
    /**
     * 树表的名字字段编号
     *
     * 名字的用途：新增或修改时，select 框展示的字段
     *
     * 关联 {@link CodegenColumnDO#getId()}
     */
    private Long treeNameColumnId;

}
