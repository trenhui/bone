package com.bone.core.tenant.context;

import com.alibaba.ttl.TransmittableThreadLocal;

/**
 * 租户上下文管理器（支持线程池级上下文传递）
 */
public class TenantContext {

    /**
     * 租户ID上下文（支持线程池任务传递）
     */
    private static final TransmittableThreadLocal<Long> TENANT_ID_CONTEXT = new TransmittableThreadLocal<>();

     /**
     * 设置当前线程的租户ID（会传递到子线程及线程池任务）
     * @param tenantId 租户ID
     */
    public static void setTenantId(Long tenantId) {
        TENANT_ID_CONTEXT.set(tenantId);
    }

    /**
     * 获取当前线程的租户ID
     */
    public static Long getTenantId() {
        return TENANT_ID_CONTEXT.get();
    }



    /**
     * 清除所有租户上下文（防止内存泄漏）
     */
    public static void clear() {
        TENANT_ID_CONTEXT.remove();
    }
}