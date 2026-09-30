package com.bone.platform.alert.domain.gateway;

/**
 * 当前租户提供者端口（N-1）。应用层经此端口读取调用方租户 ID，不直取 {@code TenantContext}。
 *
 * <p>实现：{@code infrastructure/gateway/AlertTenantProviderGatewayAdapter}。
 *
 * <p><b>为何返回 {@code Long} 而不是 {@code long}</b>：通知服务有两类特殊调用方——尚未写入租户上下文的内部入口， 与平台租户（{@code 0}）。若用
 * {@code long} 并以 0 兜底，两者会被压成同一个值， 「未认证就访问站内信」与「以平台租户身份访问」将无法区分。故此处保留 {@code null} 语义，判定交给调用方显式书写。
 *
 * <p><b>为何不用静态工具类</b>：静态调用是隐藏依赖，单测无法替换租户上下文；经端口注入后可 mock 驱动多租户场景。
 */
public interface TenantProvider {

  /** 当前调用方租户 ID；无租户上下文时返回 {@code null}。 */
  Long currentTenantIdOrNull();
}
