package com.bone.airag.domain.gateway;

/** 当前租户解析端口（infrastructure 用 {@code TenantContext} 适配）。 */
public interface TenantProvider {
  Long currentTenantId();
}
