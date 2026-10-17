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

  /** 出口 PII 脱敏开关（默认开启：个人信息不出域应是默认行为，OWASP LLM02）。 */
  private boolean piiRedactionEnabled = true;

  /** 结构化输出校验开关（{@code jsonResponse=true} 时校验响应为合法 JSON）。 */
  private boolean outputValidationEnabled = true;

  /**
   * 提示注入防护开关（OWASP LLM01，默认关闭）。
   *
   * <p>默认关闭是刻意：默认实现为关键词启发式，保险条款等业务文本里偶含「忽略 / 系统提示」等词会误伤；需要语义级检测时 以 {@code @Bean
   * PromptInjectionGuard} 覆盖为模型分类器 / 云端护栏（属外部依赖，AGENTS L3）。
   */
  private boolean promptInjectionGuardEnabled;

  /**
   * 精确响应缓存：条数上限（0 = 关闭缓存，默认关闭）。
   *
   * <p>默认关闭是保守选择：开启后同一确定性请求会复用上次模型输出，不产生新的证据/计量事件—— 对「每次调用都要留痕」的审计口径是可见的行为变化，应由部署方显式开启并同步调整审计预期。
   */
  private int cacheMaxEntries;

  /** 精确响应缓存：TTL 秒（0 = 不过期，仅受条数上限约束）。 */
  private long cacheTtlSeconds = 600;

  /** 租户配额：每窗口请求数上限（0 = 关闭配额）。 */
  private long quotaMaxRequestsPerWindow;

  /** 租户配额：窗口长度秒。 */
  private long quotaWindowSeconds = 60;
}
