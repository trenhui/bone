package com.bone.metadata.catalog.domain.gateway;

/**
 * 当前租户提供者端口（DIP）。应用层经此端口读取当前租户 ID，不依赖基础设施实现。
 *
 * <p>实现：{@code infrastructure/tenant/TenantProviderAdapter}（读取 {@code TenantContext}）。
 *
 * <p><b>为何不用静态工具类</b>：原 {@code common.CatalogTenantSupport.currentTenantId()} 为静态调用，
 * 既是不可见的隐藏依赖，也无法在单测中替换租户上下文；经端口注入后，测试可 mock 驱动多租户场景。
 */
public interface TenantProvider {

  long currentTenantId();
}
