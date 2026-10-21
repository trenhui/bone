package com.bone.ai.gateway.domain.gateway;

/**
 * provider API 密钥解析端口（密钥来源与 LLM 客户端解耦）。
 *
 * <p>默认从配置读取（{@code bone.ai.gateway.api-key}）；合规要求「配置不含明文 key、出库走 bone-security（Virtual Key 语义）」时，以
 * {@code @Bean ApiKeyProvider} 覆盖为 bone-security 实现即可——客户端与热路径零改动。
 *
 * <p><b>每次调用解析而非构造期固化</b>：支持密钥轮换与短时限 Virtual Key（provider 侧签发、过期即刷新），避免为换 key 重启 / 重新装配客户端。返回
 * {@code null} / 空串表示不带 Authorization 头（本地无鉴权 provider，如 Ollama）。
 */
@FunctionalInterface
public interface ApiKeyProvider {

  /** 解析当前生效的 provider API 密钥。 */
  String resolve();
}
