package com.bone.metadata.sdk.domain.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 字段元数据模型
 */
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FieldMetadata {

    private Long id;
    private Long tenantId;
    private String appCode;
    private String bizIdentityCode;
    private String entityType;
    private String name;
    private String columnName;
    private String dataType;
    private boolean isPrimaryKey;
    private boolean isNullable;
    private String defaultValue;
    private String constraints;
    private boolean isVirtual;
    private boolean isExtension;

    @Builder.Default
    private Boolean deleted = false;

    private Long createBy;
    private Long updateBy;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;

    @Builder.Default
    private transient Object sampleValue = null;

    // 显式添加所有需要的getter方法
    public Long getId() { return id; }
    public Long getTenantId() { return tenantId; }
    public String getAppCode() { return appCode; }
    public String getBizIdentityCode() { return bizIdentityCode; }
    public String getEntityType() { return entityType; }
    public String getName() { return name; }
    public String getColumnName() { return columnName; }
    public String getDataType() { return dataType; }
    public boolean isPrimaryKey() { return isPrimaryKey; }
    public boolean isNullable() { return isNullable; }
    public String getDefaultValue() { return defaultValue; }
    public String getConstraints() { return constraints; }
    public boolean isVirtual() { return isVirtual; }
    public boolean isExtension() { return isExtension; }
    public Boolean getDeleted() { return deleted; }
    public Long getCreateBy() { return createBy; }
    public Long getUpdateBy() { return updateBy; }
    public LocalDateTime getCreateTime() { return createTime; }
    public LocalDateTime getUpdateTime() { return updateTime; }
    public Object getSampleValue() { return sampleValue; }
    
    // 显式添加所有需要的setter方法
    public void setId(Long id) { this.id = id; }
    public void setTenantId(Long tenantId) { this.tenantId = tenantId; }
    public void setAppCode(String appCode) { this.appCode = appCode; }
    public void setBizIdentityCode(String bizIdentityCode) { this.bizIdentityCode = bizIdentityCode; }
    public void setEntityType(String entityType) { this.entityType = entityType; }
    public void setName(String name) { this.name = name; }
    public void setColumnName(String columnName) { this.columnName = columnName; }
    public void setDataType(String dataType) { this.dataType = dataType; }
    public void setPrimaryKey(boolean primaryKey) { isPrimaryKey = primaryKey; }
    public void setNullable(boolean nullable) { isNullable = nullable; }
    public void setDefaultValue(String defaultValue) { this.defaultValue = defaultValue; }
    public void setConstraints(String constraints) { this.constraints = constraints; }
    public void setVirtual(boolean virtual) { isVirtual = virtual; }
    public void setExtension(boolean extension) { isExtension = extension; }
    public void setDeleted(Boolean deleted) { this.deleted = deleted; }
    public void setCreateBy(Long createBy) { this.createBy = createBy; }
    public void setUpdateBy(Long updateBy) { this.updateBy = updateBy; }
    public void setCreateTime(LocalDateTime createTime) { this.createTime = createTime; }
    public void setUpdateTime(LocalDateTime updateTime) { this.updateTime = updateTime; }
    public void setSampleValue(Object sampleValue) { this.sampleValue = sampleValue; }

    public boolean isStringType() {
        return "STRING".equalsIgnoreCase(dataType);
    }

    public boolean isNumberType() {
        return "NUMBER".equalsIgnoreCase(dataType) || "INTEGER".equalsIgnoreCase(dataType);
    }

    public boolean isDateType() {
        return "DATE".equalsIgnoreCase(dataType);
    }

    public String toSqlMapping() {
        return isExtension ? "e." + columnName : "m." + columnName;
    }
}