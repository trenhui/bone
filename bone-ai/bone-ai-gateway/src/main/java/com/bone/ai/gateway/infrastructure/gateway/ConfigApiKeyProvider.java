package com.bone.ai.gateway.infrastructure.gateway;

import com.bone.ai.gateway.config.LlmGatewayProperties;
import com.bone.ai.gateway.domain.gateway.ApiKeyProvider;

/**
 * 默认 {@link ApiKeyProvider}：按调用从配置 {@code bone.ai.gateway.api-key} 解析。
 *
 * <p>存在意义是让「端口已设计、零消费方」的缝真正接通：此前客户端构造期固化 {@code String apiKey}，密钥轮换 / 短时限
 * Virtual Key 无从生效（换 key 必须重启）。本实现保证默认路径也走按调用解析语义——接入方以 {@code @Bean} 覆盖为
 * bone-security Virtual Key 实现时，客户端与热路径零改动（详设 §6.1 密钥托管）。
 *
 * <p>与 bone-ai-rag 的 {@code NoDocAcl} / gateway 的 {@code TenantContextProviderAdapter} 同构：默认实现兜底、
 * {@code @ConditionalOnMissingBean} 装配、覆盖即生效。空值透传（本地无鉴权 provider 如 Ollama 可留空 key）。
 */
public class ConfigApiKeyProvider implements ApiKeyProvider {

  private final LlmGatewayProperties props;

  public ConfigApiKeyProvider(LlmGatewayProperties props) {
    this.props = props;
  }

  @Override
  public String resolve() {
    // 每次调用现取而非构造期固化：配置热更新（Nacos/ConfigMap）后无需重启即换 key
    return props.getApiKey();
  }
}
