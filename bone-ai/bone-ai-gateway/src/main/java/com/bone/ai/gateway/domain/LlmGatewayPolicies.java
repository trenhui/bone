package com.bone.ai.gateway.domain;

import com.bone.ai.gateway.domain.gateway.ContentSafetyGuard;
import com.bone.ai.gateway.domain.gateway.LlmMetrics;
import com.bone.ai.gateway.domain.gateway.LlmResponseCache;
import com.bone.ai.gateway.domain.gateway.PiiRedactor;
import com.bone.ai.gateway.domain.gateway.PromptInjectionGuard;
import com.bone.ai.gateway.domain.gateway.StructuredOutputValidator;
import com.bone.ai.gateway.domain.gateway.TenantQuotaGuard;

/**
 * 网关治理策略集合（门面执行链的可插拔策略组）。
 *
 * <p>聚成一个对象是为了让门面构造签名稳定：治理面还会继续长（语义缓存、语义路由、内容安全）， 每加一项都改构造器会让所有装配点与测试跟着改；收进策略组后，新增策略只在组里加字段。
 *
 * <p>任一策略为 {@code null} 表示不启用（直通），装配层按配置决定是否注入。
 *
 * @param piiRedactor 出口 PII 脱敏（OWASP LLM02）
 * @param outputValidator 结构化输出校验（{@code jsonResponse=true} 时生效）
 * @param responseCache 精确响应缓存（仅 temperature=0）
 * @param quotaGuard 租户配额闸门
 * @param injectionGuard 提示注入防护（OWASP LLM01，仅 user 消息，默认关闭）
 * @param metrics 可观测性发射点（成本 / 延迟 / 错误 / 熔断；缺省 = Noop，该维度不观测）
 * @param contentSafetyGuard 输出内容安全（OWASP LLM05/09，默认关闭，启用方自担误伤）
 */
public record LlmGatewayPolicies(
    PiiRedactor piiRedactor,
    StructuredOutputValidator outputValidator,
    LlmResponseCache responseCache,
    TenantQuotaGuard quotaGuard,
    PromptInjectionGuard injectionGuard,
    LlmMetrics metrics,
    ContentSafetyGuard contentSafetyGuard) {

  /**
   * 兼容旧调用点：仅 5 项基础策略（不含量化发射点 / 内容安全）。
   *
   * <p>2026-10-21 补齐：策略组按设计继续长（度量 / 内容安全两个端口已就位），用重载承接旧形态—— 新增治理维度不再波及既有装配点与测试。
   */
  public LlmGatewayPolicies(
      PiiRedactor piiRedactor,
      StructuredOutputValidator outputValidator,
      LlmResponseCache responseCache,
      TenantQuotaGuard quotaGuard,
      PromptInjectionGuard injectionGuard) {
    this(piiRedactor, outputValidator, responseCache, quotaGuard, injectionGuard, null, null);
  }

  /** 兼容旧调用点：含量化发射点，无内容安全。 */
  public LlmGatewayPolicies(
      PiiRedactor piiRedactor,
      StructuredOutputValidator outputValidator,
      LlmResponseCache responseCache,
      TenantQuotaGuard quotaGuard,
      PromptInjectionGuard injectionGuard,
      LlmMetrics metrics) {
    this(piiRedactor, outputValidator, responseCache, quotaGuard, injectionGuard, metrics, null);
  }

  /** 全直通（无任何治理策略；POC / 单测默认）。 */
  public static LlmGatewayPolicies permissive() {
    return new LlmGatewayPolicies(null, null, null, null, null, null, null);
  }
}
