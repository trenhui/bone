package com.bone.metadata.sdk.domain.annotation;

/**
 * {@link TenantScope} 的作用模式。
 *
 * <p>与 ADR-0029（Criteria 通道自动租户注入）保持同一不变量：租户表查询必须被限定到单一租户， 缺可信上下文即失败关闭——绝不以 {@code tenant_id =
 * NULL} 静默空结果或跨租户越权。
 */
public enum TenantScopeMode {

  /**
   * 自动从可信 {@code TenantContext} 注入租户条件（失败关闭）。 调用方不再需要在 SQL 中手写 {@code tenant_id = #{tenantId}}
   * 也不再传递 {@code tenantId} 参数；SDK 用上下文中的租户值填充。 适合绝大多数"当前租户"查询。
   */
  AUTO,

  /** 由调用方自行在 SQL 中写入租户条件（历史默认行为，向后兼容）。 现有未标注 {@link TenantScope} 的 {@code @Sql} 方法等同此模式，行为不变。 */
  MANUAL,

  /** 全租户扫描：不注入、不限定租户。供已登记的运维型定时任务使用（定时线程无请求上下文， 若按"当前租户"扫描只会落到降级后的平台租户 0，其余租户数据永不处理）。 */
  ALL,

  /** 平台逃生舱：跳过租户过滤且不要求 {@code TenantContext}。仅用于确实要越租户的基础设施链路， 须 {@code platform:*} 授权 + 审计。 */
  BYPASS
}
