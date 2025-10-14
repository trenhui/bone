package com.bone.tool.codegen.domain.entity;

import java.io.Serializable;

/**
 * 简单的代码生成列类，不依赖任何外部库
 */
public class SimpleColumn implements Serializable {
    private static final long serialVersionUID = 1L;

    private Long id;
    private Long tableId;
    private String columnName;
    private String dataType;
    private String description;
    private String javaType;
    private String javaField;
    private Boolean primaryKey;
    private Boolean autoIncrement;
    private Boolean notNull;
    private Boolean insertable;
    private Boolean updatable;
    private Boolean listable;
    private Boolean queryable;
    private String queryType;
    private String showType;
    private String dictType;
    private String relationTableName;
    private String relationShowField;
    private String relationQueryField;
    private String extraAttrs;

    // Getters and Setters
    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getTableId() {
        return tableId;
    }

    public void setTableId(Long tableId) {
        this.tableId = tableId;
    }

    public String getColumnName() {
        return columnName;
    }

    public void setColumnName(String columnName) {
        this.columnName = columnName;
    }

    public String getDataType() {
        return dataType;
    }

    public void setDataType(String dataType) {
        this.dataType = dataType;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getJavaType() {
        return javaType;
    }

    public void setJavaType(String javaType) {
        this.javaType = javaType;
    }

    public String getJavaField() {
        return javaField;
    }

    public void setJavaField(String javaField) {
        this.javaField = javaField;
    }

    public Boolean getPrimaryKey() {
        return primaryKey;
    }

    public void setPrimaryKey(Boolean primaryKey) {
        this.primaryKey = primaryKey;
    }

    public Boolean getAutoIncrement() {
        return autoIncrement;
    }

    public void setAutoIncrement(Boolean autoIncrement) {
        this.autoIncrement = autoIncrement;
    }

    public Boolean getNotNull() {
        return notNull;
    }

    public void setNotNull(Boolean notNull) {
        this.notNull = notNull;
    }

    public Boolean getInsertable() {
        return insertable;
    }

    public void setInsertable(Boolean insertable) {
        this.insertable = insertable;
    }

    public Boolean getUpdatable() {
        return updatable;
    }

    public void setUpdatable(Boolean updatable) {
        this.updatable = updatable;
    }

    public Boolean getListable() {
        return listable;
    }

    public void setListable(Boolean listable) {
        this.listable = listable;
    }

    public Boolean getQueryable() {
        return queryable;
    }

    public void setQueryable(Boolean queryable) {
        this.queryable = queryable;
    }

    public String getQueryType() {
        return queryType;
    }

    public void setQueryType(String queryType) {
        this.queryType = queryType;
    }

    public String getShowType() {
        return showType;
    }

    public void setShowType(String showType) {
        this.showType = showType;
    }

    public String getDictType() {
        return dictType;
    }

    public void setDictType(String dictType) {
        this.dictType = dictType;
    }

    public String getRelationTableName() {
        return relationTableName;
    }

    public void setRelationTableName(String relationTableName) {
        this.relationTableName = relationTableName;
    }

    public String getRelationShowField() {
        return relationShowField;
    }

    public void setRelationShowField(String relationShowField) {
        this.relationShowField = relationShowField;
    }

    public String getRelationQueryField() {
        return relationQueryField;
    }

    public void setRelationQueryField(String relationQueryField) {
        this.relationQueryField = relationQueryField;
    }

    public String getExtraAttrs() {
        return extraAttrs;
    }

    public void setExtraAttrs(String extraAttrs) {
        this.extraAttrs = extraAttrs;
    }

    @Override
    public String toString() {
        return "SimpleColumn{" +
                "id=" + id +
                ", tableId=" + tableId +
                ", columnName='" + columnName + '\'' +
                ", dataType='" + dataType + '\'' +
                ", description='" + description + '\'' +
                ", javaType='" + javaType + '\'' +
                ", javaField='" + javaField + '\'' +
                ", primaryKey=" + primaryKey +
                '}';
    }
}