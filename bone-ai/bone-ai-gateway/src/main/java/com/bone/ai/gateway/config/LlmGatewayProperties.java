package com.bone.ai.gateway.config;

import java.time.Duration;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** bone-ai-gateway 配置项（{@code bone.ai.gateway.*}）。 */
@Getter
@Setter
@ConfigurationProperties(prefix = "bone.ai.gateway")
public class LlmGatewayProperties {

  /** OpenAI 兼容服务 base url（如 {@code https://api.openai.com/v1} 或本地 vLLM）；为空则使用 Mock 实现。 */
  private String baseUrl;

  /** API Key；本地无鉴权服务可留空。 */
  private String apiKey;

  /** 默认模型档位。 */
  private String model = "gpt-4o-mini";

  /** 出站超时。 */
  private Duration timeout = Duration.ofSeconds(30);
}
