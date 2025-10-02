package com.bone.metadata.sdk.extension;

import com.bone.metadata.sdk.domain.enums.ExtensionMode;
import lombok.Data;

import java.util.Map;

@Data
public class ExtensionContext {
    private final Long tenantId;
    private String appCode;
    private final String bizIdentityCode;
    private final String entityType;
    private final Object entityId;
    private final Map<String, Object> extraProperties;
    private final ExtensionMode mode;

    public String getEntityTable() {
        return entityType;
    }


    // 显式定义全参构造器
    public ExtensionContext(Long tenantId, String appCode, String bizIdentityCode, String entityType, Object entityId, Map<String, Object> extraProperties, ExtensionMode mode) {
        this.tenantId = tenantId;
        this.appCode = appCode;
        this.bizIdentityCode = bizIdentityCode;
        this.entityType = entityType;
        this.entityId = entityId;
        this.extraProperties = extraProperties;
        this.mode = mode;
    }

    // 原有静态工厂方法保持不变
    public static ExtensionContext of(Long tenantId, String appCode, String bizIdentityCode, String entityType, Object entityId) {
        return new ExtensionContext(tenantId, appCode, bizIdentityCode, entityType, entityId, null, ExtensionMode.RESERVED_COLUMNS);
    }

    public static ExtensionContext of(Long tenantId, String appCode, String bizIdentityCode, String entityType, Object entityId, Map<String, Object> extraProperties) {
        return new ExtensionContext(tenantId, appCode, bizIdentityCode, entityType, entityId, extraProperties, ExtensionMode.RESERVED_COLUMNS);
    }

    public static ExtensionContext of(Long tenantId, String appCode, String bizIdentityCode, String entityType, Object entityId, Map<String, Object> extraProperties, ExtensionMode mode) {
        return new ExtensionContext(tenantId, appCode, bizIdentityCode, entityType, entityId, extraProperties, mode);
    }

    public boolean isNewEntity() {
        return entityId == null
                || (entityId instanceof String && ((String) entityId).isBlank());
    }

}