package com.bone.tool.codegen.domain.entity;

import com.bone.core.annotation.Id;
import com.bone.core.domain.entity.AbstractEntity;
import com.bone.metadata.sdk.domain.annotation.Table;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

/**
 * 代码生成表配置 领域实体
 * <p>
 * 表示代码生成功能中的表配置信息，是代码生成领域的核心聚合根
 *
 * @author bone-team
 */
@Table("infra_codegen_table")
@Data
@Accessors(chain = true)
@EqualsAndHashCode(callSuper = true)
public class CodegenTable extends AbstractEntity<Long> {

    /**
     * 表配置ID
     * <p>
     * 唯一标识一个代码生成表配置
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
     * 数据源配置ID
     * <p>
     * 关联DataSourceConfigDO实体，指定表所在的数据源
     */
    private Long dataSourceConfigId;
    
    /**
     * 生成场景类型
     * <p>
     * 枚举值，表示代码生成的业务场景，决定生成代码的特性和模板选择
     */
    private Integer scene;

    // ========== 数据库表相关属性 ==========

    /**
     * 表名称
     * <p>
     * 对应数据库中的实际表名
     */
    private String tableName;
    
    /**
     * 表描述
     * <p>
     * 数据库表的注释描述
     */
    private String tableComment;
    
    /**
     * 备注信息
     * <p>
     * 用于存储额外的说明信息
     */
    private String remark;

    // ========== Java类相关属性 ==========

    /**
     * 模块名称
     * <p>
     * 如：system、infra、tool等，对应一级目录结构
     */
    private String moduleName;
    
    /**
     * 包名称
     * <p>
     * 如：com.bone.system等
     */
    private String packgeName;
    
    /**
     * 业务名称
     * <p>
     * 如：user、permission、dict等，对应二级目录结构
     */
    private String businessName;
    
    /**
     * Java类名
     * <p>
     * 首字母大写的驼峰命名，如：SysUser、SysMenu、SysDictData
     */
    private String className;
    
    /**
     * 类描述
     * <p>
     * 生成Java类的注释描述
     */
    private String classComment;
    
    /**
     * 作者
     * <p>
     * 生成代码的作者信息
     */
    private String author;

    // ========== 模板配置相关属性 ==========

    /**
     * 模板类型
     * <p>
     * 表示使用的代码生成模板类型，决定生成代码的结构和风格
     */
    private Integer templateType;

    // ========== 菜单相关属性 ==========

    /**
     * 父菜单ID
     * <p>
     * 关联菜单表的主键ID
     */
    private Long parentMenuId;

    // ========== 主子表关系相关属性 ==========

    /**
     * 主表ID
     * <p>
     * 关联当前实体的主键ID，表示主从表关系
     */
    private Long masterTableId;
    
    /**
     * 子表关联主表的字段ID
     * <p>
     * 关联CodegenColumnDO的主键ID
     */
    private Long subJoinColumnId;
    
    /**
     * 是否一对多关系
     * <p>
     * true: 一对多关系，表示一个主表记录对应多个子表记录
     * false: 一对一关系，表示一个主表记录对应一个子表记录
     */
    private Boolean subJoinMany;

    // ========== 树结构相关属性 ==========

    /**
     * 树表父字段ID
     * <p>
     * 关联CodegenColumnDO的主键ID，表示树结构的父节点字段
     */
    private Long treeParentColumnId;
    
    /**
     * 树表名称字段ID
     * <p>
     * 关联CodegenColumnDO的主键ID，表示在界面选择时展示的名称字段
     */
    private Long treeNameColumnId;

    public Long getDataSourceConfigId() {
        return dataSourceConfigId;
    }
    
    public void setDataSourceConfigId(Long dataSourceConfigId) {
        this.dataSourceConfigId = dataSourceConfigId;
    }
    
    public Integer getScene() {
        return scene;
    }
    
    public void setScene(Integer scene) {
        this.scene = scene;
    }
    
    public String getTableName() {
        return tableName;
    }
    
    public void setTableName(String tableName) {
        this.tableName = tableName;
    }
    
    public String getTableComment() {
        return tableComment;
    }
    
    public void setTableComment(String tableComment) {
        this.tableComment = tableComment;
    }
    
    public String getRemark() {
        return remark;
    }
    
    public void setRemark(String remark) {
        this.remark = remark;
    }
    
    public String getModuleName() {
        return moduleName;
    }
    
    public void setModuleName(String moduleName) {
        this.moduleName = moduleName;
    }
    
    public String getPackgeName() {
        return packgeName;
    }
    
    public void setPackgeName(String packgeName) {
        this.packgeName = packgeName;
    }
    
    public String getBusinessName() {
        return businessName;
    }
    
    public void setBusinessName(String businessName) {
        this.businessName = businessName;
    }
    
    public String getClassName() {
        return className;
    }
    
    public void setClassName(String className) {
        this.className = className;
    }
    
    public String getClassComment() {
        return classComment;
    }
    
    public void setClassComment(String classComment) {
        this.classComment = classComment;
    }
    
    public String getAuthor() {
        return author;
    }
    
    public void setAuthor(String author) {
        this.author = author;
    }
    
    public Integer getTemplateType() {
        return templateType;
    }
    
    public void setTemplateType(Integer templateType) {
        this.templateType = templateType;
    }
    
    public Long getParentMenuId() {
        return parentMenuId;
    }
    
    public void setParentMenuId(Long parentMenuId) {
        this.parentMenuId = parentMenuId;
    }
    
    public Long getMasterTableId() {
        return masterTableId;
    }
    
    public void setMasterTableId(Long masterTableId) {
        this.masterTableId = masterTableId;
    }
    
    public Long getSubJoinColumnId() {
        return subJoinColumnId;
    }
    
    public void setSubJoinColumnId(Long subJoinColumnId) {
        this.subJoinColumnId = subJoinColumnId;
    }
    
    public Boolean getSubJoinMany() {
        return subJoinMany;
    }
    
    public void setSubJoinMany(Boolean subJoinMany) {
        this.subJoinMany = subJoinMany;
    }
    
    public Long getTreeParentColumnId() {
        return treeParentColumnId;
    }
    
    public void setTreeParentColumnId(Long treeParentColumnId) {
        this.treeParentColumnId = treeParentColumnId;
    }
    
    public Long getTreeNameColumnId() {
        return treeNameColumnId;
    }
    
    public void setTreeNameColumnId(Long treeNameColumnId) {
        this.treeNameColumnId = treeNameColumnId;
    }
}
