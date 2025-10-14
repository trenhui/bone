package com.bone.tool.codegen.domain.entity;

/**
 * 表字段实体类
 * 封装数据库表中的字段信息，用于代码生成过程
 *
 * @author bone-team
 */
public class TableField {
    private String name; // 字段名
    private String type; // 字段类型
    private String propertyName; // Java属性名
    private String comment; // 字段注释
    private boolean primaryKey; // 是否为主键
    private boolean fill; // 是否需要填充
    private boolean hasComment; // 是否有列注释
    
    // 默认构造函数
    public TableField() {
    }
    
    // 带参数的构造函数
    public TableField(String name, String type, String propertyName, String comment) {
        this.name = name;
        this.type = type;
        this.propertyName = propertyName;
        this.comment = comment;
    }
    
    // getter和setter方法
    public String getName() {
        return name;
    }
    
    public void setName(String name) {
        this.name = name;
    }
    
    public String getType() {
        return type;
    }
    
    public void setType(String type) {
        this.type = type;
    }
    
    public String getPropertyName() {
        return propertyName;
    }
    
    public void setPropertyName(String propertyName) {
        this.propertyName = propertyName;
    }
    
    public String getComment() {
        return comment;
    }
    
    public void setComment(String comment) {
        this.comment = comment;
    }
    
    public boolean isFill() {
        return fill;
    }
    
    public void setFill(boolean fill) {
        this.fill = fill;
    }
    
    public boolean isPrimaryKey() {
        return primaryKey;
    }
    
    public void setPrimaryKey(boolean primaryKey) {
        this.primaryKey = primaryKey;
    }
    
    public boolean isHasComment() {
        return hasComment;
    }
    
    public void setHasComment(boolean hasComment) {
        this.hasComment = hasComment;
    }
}