package com.bone.ai.gateway.domain.gateway;

/**
 * LLM 网关可观测性端口（成本 / 延迟 / 错误 / 熔断指标的发射点）。
 *
 * <p>网关是企业 LLM 成本与稳定性的唯一收口，没有指标即「盲飞」——无法回答哪个租户 / 模型烧了多少钱、P99 多少、provider
 * 是否在降级。本端口把观测<b>发射点</b>固化在领域契约上，与具体后端（Micrometer / Prometheus / OTel {@code gen_ai.*}）解耦：
 *
 * <ul>
 *   <li>默认 {@code NoopLlmMetrics}（零依赖，库形态不强制引入度量框架）；
 *   <li>接入 Micrometer（依赖经架构师审批后）只需一个实现本接口的 {@code @Bean} 适配器，热路径埋点无需再改。
 * </ul>
 *
 * <p>全部方法默认空实现：适配器按需只覆写关心的指标，缺省即「该维度不观测」而非报错。
 */
public interface LlmMetrics {

  /** 成本红线短路（规则命中未触达模型）。 */
  default void recordShortCircuited(String tenantId, String model) {}

  /** 精确缓存命中（未触达模型）。 */
  default void recordCacheHit(String tenantId, String model) {}

  /** 配额超限拒绝（429）。 */
  default void recordQuotaExceeded(String tenantId, String model) {}

  /** provider 调用结果（成功 / 失败 + 延迟毫秒）——延迟 P99 与错误率的核心。 */
  default void recordProviderCall(String tenantId, String model, boolean success, long latencyMs) {}

  /** token 计量（成本核心：按租户 / 模型聚合 prompt / completion / total）。 */
  default void recordTokens(
      String tenantId, String model, long promptTokens, long completionTokens, long totalTokens) {}
}
