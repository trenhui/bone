package com.bone.ai.gateway.config;

import jakarta.annotation.PostConstruct;
import java.time.Duration;
import lombok.Getter;
import lombok.Setter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** bone-ai-gateway 配置项（{@code bone.ai.gateway.*}）。 */
@Getter
@Setter
@ConfigurationProperties(prefix = "bone.ai.gateway")
public class LlmGatewayProperties {

  private static final Logger log = LoggerFactory.getLogger(LlmGatewayProperties.class);

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

  /**
   * 多副本部署时是否优先用 Redis 分布式配额（默认 true；仅当容器存在 {@code RedissonClient} bean 时生效）。
   *
   * <p>关闭 = 即使有 Redis 也用进程内计数（单副本私有化、或刻意让配额按副本独立的场景）。 分布式实现见 {@code
   * RedisQuotaAutoConfiguration}；进程内实现在多副本下配额为副本数 N 倍，详设 §6.4。
   */
  private boolean quotaRedisEnabled = true;

  /**
   * 启动期密钥自检（生产就绪硬依赖「密钥托管」的可落地部分，详设 §6.1）。
   *
   * <p>两条规则：①{@code api-key} 里还留着未解析的 {@code ${...}} 占位符 ⇒ <b>启动即失败</b>——这几乎总是「把模板配置
   * 原样提交、环境变量没注」的事故，与其让第一次模型调用带着字面量 {@code ${BONE_AI_GATEWAY_API_KEY}} 去鉴权失败，
   * 不如在启动就炸；②远端 https provider 却空 key ⇒ WARN（本地 vLLM 无鉴权可空，远端空 key 大概率是漏配）。
   *
   * <p>注意②对 Virtual Key 部署形态的已知噪声：以 {@code @Bean ApiKeyProvider} 覆盖为 bone-security 实现时，配置态
   * {@code api-key} 本应为空（key 走托管出库）——此 WARN 会照常触发，属预期，可按部署清单忽略；默认配置路径下该 WARN
   * 仍是有效的漏配信号，故不做静默。
   *
   * <p>真正的密钥托管（bone-security Virtual Key 出库、配置库零明文）属 L3 项；此处保证「占位符不漏到线上、空 key 有感知」。
   */
  @PostConstruct
  void validateProviderSecrets() {
    if (baseUrl == null || baseUrl.isBlank()) {
      return; // 未配 provider：Mock 形态，无密钥可校
    }
    if (apiKey != null && apiKey.contains("${")) {
      throw new IllegalStateException(
          "bone.ai.gateway.api-key 含未解析占位符「"
              + apiKey
              + "」：密钥须经环境变量 / 密钥托管注入（如 ${BONE_AI_GATEWAY_API_KEY} 先在部署环境定义），"
              + "禁止把占位符或明文密钥提交进配置库");
    }
    if ((apiKey == null || apiKey.isBlank()) && baseUrl.startsWith("https")) {
      log.warn(
          "Remote provider configured without api-key (base-url={}): local no-auth inference may leave it empty, remote https usually indicates a missing secret",
          baseUrl);
    }
  }
}
