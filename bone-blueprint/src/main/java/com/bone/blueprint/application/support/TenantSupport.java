package com.bone.blueprint.application.support;

import com.bone.core.tenant.context.TenantContext;

/**
 * 租户上下文辅助（读/写路径强制租户隔离键）。
 */
public final class TenantSupport {

    private static final long DEFAULT_TENANT_ID = 0L;

    private TenantSupport() {}

    public static long currentTenantId() {
        Long tenantId = TenantContext.getTenantId();
        return tenantId != null ? tenantId : DEFAULT_TENANT_ID;
    }
}
