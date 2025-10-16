package com.bone.tool.codegen.domain.entity;

import java.io.Serializable;
import java.util.List;
import lombok.Data;

/**
 * 表信息
 */
@Data
public class TableInfo implements Serializable {
    private static final long serialVersionUID = 1L;
    
    private String tableName; // 表名
    private String tableComment; // 表注释
    private String entityName; // 实体类名
    private String moduleName; // 模块名称
    private List<CodegenColumn> fieldList; // 字段列表
    
    // 兼容DatabaseTableService中的方法调用
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
    
    public void setFieldName(String moduleName) {
        this.moduleName = moduleName;
    }
}