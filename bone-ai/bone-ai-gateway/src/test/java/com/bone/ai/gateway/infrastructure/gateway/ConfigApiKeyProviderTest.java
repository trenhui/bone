package com.bone.ai.gateway.infrastructure.gateway;

import static org.assertj.core.api.Assertions.assertThat;

import com.bone.ai.gateway.config.LlmGatewayProperties;
import org.junit.jupiter.api.Test;

class ConfigApiKeyProviderTest {

  @Test
  void resolve_returns_configured_key() {
    LlmGatewayProperties props = new LlmGatewayProperties();
    props.setApiKey("sk-configured");

    assertThat(new ConfigApiKeyProvider(props).resolve()).isEqualTo("sk-configured");
  }

  @Test
  void resolve_passes_through_null_and_blank_local_no_auth_providers() {
    // 本地无鉴权 provider（Ollama / vLLM）可留空 key——空值透传由客户端统一解释为「不带 Authorization 头」
    LlmGatewayProperties nullProps = new LlmGatewayProperties();
    assertThat(new ConfigApiKeyProvider(nullProps).resolve()).isNull();

    LlmGatewayProperties blankProps = new LlmGatewayProperties();
    blankProps.setApiKey("   ");
    assertThat(new ConfigApiKeyProvider(blankProps).resolve()).isEqualTo("   ");
  }

  @Test
  void resolve_reads_fresh_each_call_config_hot_update_takes_effect() {
    // 按调用现取而非构造期固化：Nacos/ConfigMap 热更新换 key 后无需重启（短时限 Virtual Key 轮换的同源语义）
    LlmGatewayProperties props = new LlmGatewayProperties();
    props.setApiKey("sk-old");
    ConfigApiKeyProvider provider = new ConfigApiKeyProvider(props);

    assertThat(provider.resolve()).isEqualTo("sk-old");
    props.setApiKey("sk-rotated");
    assertThat(provider.resolve()).isEqualTo("sk-rotated");
  }
}
