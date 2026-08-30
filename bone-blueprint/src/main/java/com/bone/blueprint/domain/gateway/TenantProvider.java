package com.bone.blueprint.domain.gateway;

/**
 * 当前租户提供者端口（DIP）。应用层经此端口读取当前租户 ID，不依赖基础设施实现。
 *
 * <p>实现：{@code infrastructure/context/TenantProviderAdapter}（读取 {@code TenantContext}）。
 */
public interface TenantProvider {

  long currentTenantId();
}
