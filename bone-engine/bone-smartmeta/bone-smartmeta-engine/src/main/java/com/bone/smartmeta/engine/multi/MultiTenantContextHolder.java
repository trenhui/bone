package com.bone.smartmeta.engine.multi;

/**
 * 多租户上下文持有者接口
 * 用于管理租户ID的上下文
 */
public interface MultiTenantContextHolder {
    
    /**
     * 获取当前租户ID
     * @return 当前租户ID
     */
    String getCurrentTenantId();
    
    /**
     * 设置当前租户ID
     * @param tenantId 租户ID
     */
    void setCurrentTenantId(String tenantId);
    
    /**
     * 清除当前租户ID
     */
    void clear();
    
    /**
     * 获取默认租户ID
     * @return 默认租户ID
     */
    String getDefaultTenantId();
}