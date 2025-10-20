package com.bone.smartmeta.engine.tenant;

/**
 * 多租户上下文类
 * 支持线程级别的租户ID传递
 */
public class TenantContext {
    
    private static final ThreadLocal<String> CURRENT_TENANT = new ThreadLocal<>();
    
    // 系统租户ID（用于系统级元数据）
    public static final String SYSTEM_TENANT_ID = "system";
    
    /**
     * 设置当前租户ID
     */
    public static void setCurrentTenantId(String tenantId) {
        CURRENT_TENANT.set(tenantId);
    }
    
    /**
     * 获取当前租户ID
     * 如果未设置，返回系统租户
     */
    public static String getCurrentTenantId() {
        String tenantId = CURRENT_TENANT.get();
        return tenantId != null ? tenantId : SYSTEM_TENANT_ID;
    }
    
    /**
     * 清除当前租户上下文
     * 用于清理线程本地变量，避免内存泄漏
     */
    public static void clear() {
        CURRENT_TENANT.remove();
    }
    
    /**
     * 检查租户ID是否有效
     */
    public static boolean isValidTenantId(String tenantId) {
        return tenantId != null && !tenantId.trim().isEmpty();
    }
}