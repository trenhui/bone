package com.bone.tool.codegen.domain.entity;

import java.io.Serializable;
import java.util.List;
import lombok.Data;

/**
 * 数据库表元数据
 * 用于存储和传递数据库表的元数据信息
 */
@Data
public class DatabaseTableMetadata implements Serializable {
    private static final long serialVersionUID = 1L;
    
    /** 表名 */
    private String tableName;
    
    /** 表注释 */
    private String tableComment;
    
    /** 实体类名 */
    private String entityName;
    
    /** 模块名称 */
    private String moduleName;
    
    /** 字段名称 */
    private String fieldName;
    
    /** 字段列表 */
    private List<CodegenColumn> fieldList;
    
    // 兼容DatabaseTableService中的方法调用 - 保持向后兼容性
    public String getName() {
        return tableName;
    }
    
    public void setName(String tableName) {
        this.tableName = tableName;
    }
    
    public String getComment() {
        return tableComment;
    }
    
    public void setComment(String tableComment) {
        this.tableComment = tableComment;
    }
    
    public void setFields(List<CodegenColumn> fieldList) {
        this.fieldList = fieldList;
    }
    
    public List<CodegenColumn> getFields() {
        return fieldList;
    }
}