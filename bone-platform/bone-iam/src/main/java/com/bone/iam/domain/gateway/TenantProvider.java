package com.bone.iam.domain.gateway;

/**
 * 当前租户提供者端口（E-2）。应用层经此端口读取调用方租户 ID，不直取 {@code TenantContext}。
 *
 * <p>实现：{@code infrastructure/tenant/TenantProviderAdapter}。
 *
 * <p><b>为何返回 {@code Long} 而不是 {@code long}</b>：IAM 有两类特殊调用方——尚未写入租户上下文的内部入口， 与平台租户（{@code
 * 0}）。二者都允许跨租户访问，但成因不同：前者是「租户未知」，后者是「平台特权」。 若用 {@code long} 并以 0 兜底，两者会被压成同一个值，调用方无法再区分，也无法在告警里区分
 * 「未认证就查了数据」与「平台租户查了数据」。故此处保留 {@code null} 语义，判定交给调用方显式书写。
 *
 * <p><b>为何不用静态工具类</b>：静态调用是隐藏依赖，单测无法替换租户上下文；经端口注入后可 mock 驱动多租户场景。
 */
public interface TenantProvider {

  /** 当前调用方租户 ID；无租户上下文时返回 {@code null}。 */
  Long currentTenantIdOrNull();
}
