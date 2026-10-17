package com.bone.ai.gateway.domain;

import com.bone.ai.gateway.domain.gateway.LlmResponseCache;
import com.bone.ai.gateway.domain.gateway.PiiRedactor;
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
 */
public record LlmGatewayPolicies(
    PiiRedactor piiRedactor,
    StructuredOutputValidator outputValidator,
    LlmResponseCache responseCache,
    TenantQuotaGuard quotaGuard) {

  /** 全直通（无任何治理策略；POC / 单测默认）。 */
  public static LlmGatewayPolicies permissive() {
    return new LlmGatewayPolicies(null, null, null, null);
  }
}
