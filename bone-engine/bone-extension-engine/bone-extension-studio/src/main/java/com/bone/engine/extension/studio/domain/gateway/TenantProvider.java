package com.bone.engine.extension.studio.domain.gateway;

import java.util.function.Supplier;

/**
 * 当前租户提供者端口（DDD E-2）。应用层经此端口访问租户，不直取 {@code TenantContext}。
 *
 * <p>实现：{@code infrastructure/gateway/TenantProviderAdapter}。
 *
 * <p><b>为何返回 {@code Long} 而不是 {@code long}</b>：无租户上下文时（如数据面无用户会话的进程级上报） 语义为「未知」，用 {@code null}
 * 保留该语义，由调用方显式决定兜底，而非在端口内静默压成 0。
 *
 * <p><b>为何不用静态工具类</b>：静态调用是隐藏依赖，单测无法替换租户上下文；经端口注入后可 mock 驱动多租户场景。
 *
 * <p><b>为何作用域方法是本端口的原子职责</b>：{@link #runAs} 把「设租户 → 执行 → finally 清理」三步 收敛为一次调用。若拆成 {@code
 * setTenantId} + {@code clear} 两个端口方法，调用方极易漏掉 finally， 导致线程池复用时租户上下文泄漏到下一个请求 ——
 * 这是安全事故。故作用域语义必须在实现里成对闭合。
 */
public interface TenantProvider {

  /** 当前调用方租户 ID；无租户上下文时返回 {@code null}。 */
  Long currentTenantIdOrNull();

  /**
   * 在指定租户作用域内执行 {@code action}，返回其结果；执行完毕（含异常）后恢复原有租户上下文。
   *
   * <p>用于「进程级上报无用户会话」这类必须显式指定归属、且不得污染调用方上下文的场景。
   *
   * @param tenantId 作用域内使用的租户 ID
   * @param action 作用域内执行的动作
   * @param <T> 结果类型
   * @return {@code action} 的返回值
   */
  <T> T runAs(Long tenantId, Supplier<T> action);
}
