package com.bone.ai.gateway.domain.gateway;

/**
 * 租户配额闸门（防单租户刷爆成本与 provider 速率）。
 *
 * <p><b>与成本红线的分工</b>：红线是「规则已命中就别调模型」的语义短路（单请求级）；配额是「这个租户今天/这一分钟用太多了」的
 * 容量限制（租户级）。两者都必须在门面前置，否则失控租户能拖垮整个共享模型通道。
 *
 * <p><b>实现局限（必须显式声明）</b>：默认实现 {@code InMemoryTenantQuotaGuard} 是<b>进程内计数</b>——多副本部署下每个副本
 * 各算各的，全局配额会是 N 倍。单副本或私有化单体部署可用；多副本必须换 Redis 令牌桶（以 {@code @Bean} 覆盖本端口）。 这与其说是「已解决」，不如说是「有了闸门位置 +
 * 明确的升级路径」。
 */
public interface TenantQuotaGuard {

  /**
   * 尝试为一次调用取配额；超限抛业务异常（{@code AI_GATEWAY_QUOTA_EXCEEDED}）。
   *
   * @param tenantId 租户（为 null 时按单租户口径放行，POC 兼容）
   */
  void acquire(String tenantId);
}
