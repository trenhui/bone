package com.bone.tool.codegen.domain.entity;

import com.bone.core.domain.entity.AbstractEntity;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

import java.io.Serializable;
import java.util.Collections;
import java.util.Map;

// 移除不存在的@Table注解
@Accessors(chain = true)
@EqualsAndHashCode(callSuper = true)
public class CodegenTable extends AbstractEntity<Long> implements Serializable {
    private static final long serialVersionUID = 1L;
    
    private Long datasourceId;
    private String tableName;
    private String tableComment;
    private String moduleName;
    private String packageName;
    private String businessName;
    private String className;
    private String classComment;
    private String author;
    private Integer templateType;
    private Integer scene;
    private Long parentMenuId;
    private Long masterTableId;
    private Long subJoinColumnId;
    private Boolean subJoinMany;
    private Long treeParentColumnId;
    private Long treeNameColumnId;
    private Map<String, String> codeFiles;
    
    // Getters and Setters
    public Long getDatasourceId() {
        return datasourceId;
    }
    
    public void setDatasourceId(Long datasourceId) {
        this.datasourceId = datasourceId;
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
    
    public String getModuleName() {
        return moduleName;
    }
    
    public void setModuleName(String moduleName) {
        this.moduleName = moduleName;
    }
    
    public String getPackageName() {
        return packageName;
    }
    
    public void setPackageName(String packageName) {
        this.packageName = packageName;
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
    
    public Integer getScene() {
        return scene;
    }
    
    public void setScene(Integer scene) {
        this.scene = scene;
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
    
    public Map<String, String> getCodeFiles() {
        return codeFiles != null ? Collections.unmodifiableMap(codeFiles) : Collections.emptyMap();
    }
    
    public void setCodeFiles(Map<String, String> codeFiles) {
        this.codeFiles = codeFiles;
    }
}
