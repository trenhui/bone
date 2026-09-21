package com.bone.integration.domain.gateway;

/**
 * 当前租户提供者端口（DDD E-2）。应用层经此端口读取调用方租户 ID，不直取 {@code TenantContext}。
 *
 * <p>实现：{@code infrastructure/gateway/TenantProviderAdapter}。
 *
 * <p><b>为何返回 {@code Long} 而不是 {@code long}</b>：无租户上下文时（如异步/Outbox 入口尚未建立租户）语义为「未知」，
 * 用 {@code null} 保留该语义，由调用方显式决定兜底，而非在端口内静默压成 0。
 *
 * <p><b>为何不用静态工具类</b>：静态调用是隐藏依赖，单测无法替换租户上下文；经端口注入后可 mock 驱动多租户场景。
 */
public interface TenantProvider {

  /** 当前调用方租户 ID；无租户上下文时返回 {@code null}。 */
  Long currentTenantIdOrNull();
}
