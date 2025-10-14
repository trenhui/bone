package com.bone.tool.codegen.domain.entity;

import java.util.List;

/**
 * 表信息实体类
 * 封装数据库表的基本信息和字段列表，作为代码生成的核心数据模型
 *
 * @author bone-team
 */
public class TableInfo {
    private String name; // 表名
    private String comment; // 表注释
    private String entityName; // Java实体类名
    private String fieldName; // 字段名称（驼峰命名）
    private List<TableField> fields; // 表字段列表
    
    // 默认构造函数
    public TableInfo() {
    }
    
    // 带参数的构造函数
    public TableInfo(String name, String comment) {
        this.name = name;
        this.comment = comment;
        this.entityName = name;
        this.fieldName = name;
    }
    
    // getter和setter方法
    public String getName() {
        return name;
    }
    
    public void setName(String name) {
        this.name = name;
    }
    
    public String getComment() {
        return comment;
    }
    
    public void setComment(String comment) {
        this.comment = comment;
    }
    
    public String getEntityName() {
        return entityName;
    }
    
    public void setEntityName(String entityName) {
        this.entityName = entityName;
    }
    
    public String getFieldName() {
        return fieldName;
    }
    
    public void setFieldName(String fieldName) {
        this.fieldName = fieldName;
    }
    
    public List<TableField> getFields() {
        return fields;
    }
    
    public void setFields(List<TableField> fields) {
        this.fields = fields;
    }
}