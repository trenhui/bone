package com.bone.metadata.sdk.domain.model;

import com.bone.metadata.sdk.extension.ExtensionContext;
import lombok.Value;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 实体分组／列分配上下文：
 * 既是路由分区的键，也是 ColumnAllocator 的上下文载体。
 */
public class AllocationContext {
    /**
     * 租户 ID
     */
    private Long tenantId;
    /**
     * 应用编码
     */
    private String appCode;
    /**
     * 业务身份（如业务线/模块）
     */
    private String bizIdentityCode;
    /**
     * 实体类型（如 Order、User 等）
     */
    private String entityType;
    
    public AllocationContext(Long tenantId, String appCode, String bizIdentityCode, String entityType) {
        this.tenantId = tenantId;
        this.appCode = appCode;
        this.bizIdentityCode = bizIdentityCode;
        this.entityType = entityType;
    }
    
    public Long getTenantId() { return tenantId; }
    public String getAppCode() { return appCode; }
    public String getBizIdentityCode() { return bizIdentityCode; }
    public String getEntityType() { return entityType; }


    /**
     * 从 ExtensionContext 构造 AllocationContext
     */
    public static AllocationContext from(ExtensionContext ctx) {
        return new AllocationContext(
                ctx.getTenantId(),
                ctx.getAppCode(),
                ctx.getBizIdentityCode(),
                ctx.getEntityType()
        );
    }

    public static AllocationContext of(Long tenantId, String appCode, String bizIdentityCode, String entityType) {
        return new AllocationContext(
                tenantId,
                appCode,
                bizIdentityCode,
                entityType
        );
    }

    //todo more
    public static AllocationContext of(String appCode, String entityType) {
        return new AllocationContext(
                null,
                appCode,
                null,
                entityType
        );
    }


    /**
     * 分布式锁用的唯一 key
     */
    public String lockKey() {
        return String.format("alloc:%d:%s:%s:%s",
                tenantId, appCode, bizIdentityCode, entityType);
    }

    // 驼峰转下划线的小工具
    private static final Pattern CAMEL = Pattern.compile("([a-z])([A-Z])");

    private static String camelToSnake(String str) {
        Matcher m = CAMEL.matcher(str);
        return m.replaceAll(r -> r.group(1) + "_" + r.group(2)).toLowerCase(Locale.ROOT);
    }
}