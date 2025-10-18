package com.bone.metadata.sdk.domain.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

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
    
    // Getters and Setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getDataType() { return dataType; }
    public void setDataType(String dataType) { this.dataType = dataType; }
    public Long getTenantId() { return tenantId; }
    public void setTenantId(Long tenantId) { this.tenantId = tenantId; }
    public String getAppCode() { return appCode; }
    public void setAppCode(String appCode) { this.appCode = appCode; }
    public String getBizIdentityCode() { return bizIdentityCode; }
    public void setBizIdentityCode(String bizIdentityCode) { this.bizIdentityCode = bizIdentityCode; }
    public String getEntityType() { return entityType; }
    public void setEntityType(String entityType) { this.entityType = entityType; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getColumnName() { return columnName; }
    public void setColumnName(String columnName) { this.columnName = columnName; }
    public Boolean getDeleted() { return deleted; }
    public void setDeleted(Boolean deleted) { this.deleted = deleted; }
    public Long getCreateBy() { return createBy; }
    public void setCreateBy(Long createBy) { this.createBy = createBy; }
    // 只保留getId()和setId()方法，其他方法由Lombok的@Data注解自动生成
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getUpdateBy() { return updateBy; }
    public void setUpdateBy(Long updateBy) { this.updateBy = updateBy; }
    public LocalDateTime getCreateTime() { return createTime; }
    public void setCreateTime(LocalDateTime createTime) { this.createTime = createTime; }
    public LocalDateTime getUpdateTime() { return updateTime; }
    public void setUpdateTime(LocalDateTime updateTime) { this.updateTime = updateTime; }
    public boolean isPrimaryKey() { return isPrimaryKey; }
    public void setPrimaryKey(boolean primaryKey) { isPrimaryKey = primaryKey; }
    public boolean isNullable() { return isNullable; }
    public void setNullable(boolean nullable) { isNullable = nullable; }
    public String getDefaultValue() { return defaultValue; }
    public void setDefaultValue(String defaultValue) { this.defaultValue = defaultValue; }
    public String getConstraints() { return constraints; }
    public void setConstraints(String constraints) { this.constraints = constraints; }
    public boolean isVirtual() { return isVirtual; }
    public void setVirtual(boolean virtual) { isVirtual = virtual; }
    public boolean isExtension() { return isExtension; }
     public void setExtension(boolean extension) { isExtension = extension; }
    public String getColumnName() { return columnName; }

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