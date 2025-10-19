package com.bone.metadata.sdk.domain.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 字段元数据模型
 */
@Data
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