package com.bone.ai.gateway.config;

import com.bone.ai.gateway.application.LlmGatewayApplicationService;
import com.bone.ai.gateway.domain.LlmClient;
import com.bone.ai.gateway.domain.LlmGatewayPolicies;
import com.bone.ai.gateway.domain.gateway.ApiKeyProvider;
import com.bone.ai.gateway.domain.gateway.ContentSafetyGuard;
import com.bone.ai.gateway.domain.gateway.LlmMetrics;
import com.bone.ai.gateway.domain.gateway.LlmResponseCache;
import com.bone.ai.gateway.domain.gateway.ModelRouter;
import com.bone.ai.gateway.domain.gateway.PiiRedactor;
import com.bone.ai.gateway.domain.gateway.PromptInjectionGuard;
import com.bone.ai.gateway.domain.gateway.StructuredOutputValidator;
import com.bone.ai.gateway.domain.gateway.TenantProvider;
import com.bone.ai.gateway.domain.gateway.TenantQuotaGuard;
import com.bone.ai.gateway.domain.service.NoopLlmMetrics;
import com.bone.ai.gateway.infrastructure.JdkLlmTransport;
import com.bone.ai.gateway.infrastructure.LlmHttpTransport;
import com.bone.ai.gateway.infrastructure.MockLlmClient;
import com.bone.ai.gateway.infrastructure.OpenAiCompatibleClient;
import com.bone.ai.gateway.infrastructure.ResilientLlmTransport;
import com.bone.ai.gateway.infrastructure.ResilientLlmTransport.RetriableHttpException;
import com.bone.ai.gateway.infrastructure.cache.InMemoryLlmResponseCache;
import com.bone.ai.gateway.infrastructure.event.SpringDomainEventPublisher;
import com.bone.ai.gateway.infrastructure.gateway.ConfigApiKeyProvider;
import com.bone.ai.gateway.infrastructure.gateway.TenantContextProviderAdapter;
import com.bone.ai.gateway.infrastructure.quota.InMemoryTenantQuotaGuard;
import com.bone.ai.gateway.infrastructure.security.HeuristicInjectionGuard;
import com.bone.ai.gateway.infrastructure.security.JsonOutputValidator;
import com.bone.ai.gateway.infrastructure.security.RegexPiiRedactor;
import com.bone.core.domain.event.DomainEventPublisher;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import java.util.Map;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;

/**
 * bone-ai-gateway 自动配置（Boot 3 {@code .imports} 机制）。
 *
 * <p>双实现可替换（Phase 0 出口判据）：配置了 {@code bone.ai.gateway.base-url} 即走 OpenAI 兼容真实客户端，否则回落确定性 Mock；门面契约
 * {@link LlmClient} 不变，换 provider 不改业务代码。默认提供 Spring 事件总线版 {@link DomainEventPublisher}（供
 * bone-ai-trace 订阅）。
 *
 * <p>出站 PII 脱敏（{@code bone.ai.gateway.pii-redaction-enabled}，OWASP LLM02）<b>默认开启</b>——
 * 私有化交付的合规叙事要求「个人信息不出域」是默认行为而非可选项；确需透传明文（如内部自建模型、无出域风险）时显式关闭。
 */
@AutoConfiguration
@EnableConfigurationProperties(LlmGatewayProperties.class)
public class LlmGatewayAutoConfiguration {

  @Bean
  @ConditionalOnMissingBean
  public LlmGatewayApplicationService llmGatewayService(
      LlmClient llmClient,
      DomainEventPublisher domainEventPublisher,
      TenantProvider tenantProvider,
      LlmGatewayPolicies policies,
      ObjectProvider<ModelRouter> modelRouter) {
    return new LlmGatewayApplicationService(
        llmClient,
        domainEventPublisher,
        tenantProvider,
        policies,
        modelRouter.getIfAvailable()); // 缺省 = 透传（按请求模型原样调用）
  }

  /**
   * 治理策略组（脱敏 / 输出校验 / 缓存 / 配额 / 注入防护 / 度量 / 内容安全）。
   *
   * <p>各项均可按配置关闭；默认开启脱敏与输出校验（合规默认），缓存与配额默认关闭（需按部署容量显式开启）。
   *
   * <p>2026-10-21 补齐：{@code LlmMetrics} 与 {@code ContentSafetyGuard} 两个端口此前已就位却无消费方—— 接入方以
   * {@code @Bean} 覆盖即被忽略 （配了不生效）。此处按「有则用、无则不启用」消费：度量缺省 Noop，内容安全默认关闭（启发式误伤风险，启用方自担）。
   */
  @Bean
  @ConditionalOnMissingBean
  public LlmGatewayPolicies gatewayPolicies(
      LlmGatewayProperties props,
      ObjectProvider<PiiRedactor> piiRedactor,
      ObjectProvider<StructuredOutputValidator> validator,
      ObjectProvider<LlmResponseCache> cache,
      ObjectProvider<TenantQuotaGuard> quota,
      ObjectProvider<PromptInjectionGuard> injectionGuard,
      ObjectProvider<LlmMetrics> metrics,
      ObjectProvider<ContentSafetyGuard> contentSafetyGuard) {
    PiiRedactor redactor =
        piiRedactor.getIfAvailable(() -> new RegexPiiRedactor(props.isPiiRedactionEnabled()));
    StructuredOutputValidator outputValidator =
        validator.getIfAvailable(
            () -> props.isOutputValidationEnabled() ? new JsonOutputValidator() : null);
    LlmResponseCache responseCache =
        cache.getIfAvailable(
            () ->
                props.getCacheMaxEntries() > 0
                    ? new InMemoryLlmResponseCache(
                        props.getCacheMaxEntries(), props.getCacheTtlSeconds() * 1000L)
                    : null);
    TenantQuotaGuard quotaGuard =
        quota.getIfAvailable(
            () ->
                props.getQuotaMaxRequestsPerWindow() > 0
                    ? new InMemoryTenantQuotaGuard(
                        props.getQuotaMaxRequestsPerWindow(), props.getQuotaWindowSeconds() * 1000L)
                    : null);
    // 提示注入防护默认关闭（启发式误伤风险），启用方以 @Bean 覆盖为语义级护栏亦可
    PromptInjectionGuard guard =
        injectionGuard.getIfAvailable(
            () -> props.isPromptInjectionGuardEnabled() ? new HeuristicInjectionGuard() : null);
    // 度量：缺省 Noop（零依赖）；接入 Micrometer / Prometheus 者以 @Bean 覆盖即生效
    LlmMetrics metricsPort = metrics.getIfAvailable(() -> NoopLlmMetrics.INSTANCE);
    // 输出内容安全：默认不启用（启发式误伤风险）；接入语义级 / 云端内容安全者以 @Bean 覆盖即生效
    ContentSafetyGuard safetyGuard = contentSafetyGuard.getIfAvailable(() -> null);
    return new LlmGatewayPolicies(
        redactor, outputValidator, responseCache, quotaGuard, guard, metricsPort, safetyGuard);
  }

  @Bean
  @ConditionalOnMissingBean
  public DomainEventPublisher domainEventPublisher(
      ApplicationEventPublisher applicationEventPublisher) {
    return new SpringDomainEventPublisher(applicationEventPublisher);
  }

  /**
   * 租户解析（网关侧端口）。
   *
   * <p>2026-10-21 更名（原 {@code tenantProvider}）：bone-ai 的 trace / gateway / rag 三个自动配置都注册了名为 {@code
   * tenantProvider} 的 Bean， 而 Spring Boot 3 默认禁止 Bean 定义覆盖（{@code
   * allow-bean-definition-overriding=false}）—— 三者同在一个 classpath（如 bone-ai-app 聚合）即抛 {@code
   * BeanDefinitionOverrideException}，整个上下文起不来。三者端口类型本就互不相同（按类型注入不受影响），此处按模块前缀唯一化。
   */
  @Bean
  @ConditionalOnMissingBean
  public TenantProvider gatewayTenantProvider() {
    return new TenantContextProviderAdapter();
  }

  @Bean
  @ConditionalOnMissingBean
  public LlmClient llmClient(
      LlmHttpTransport llmTransport, LlmGatewayProperties props, ApiKeyProvider apiKeyProvider) {
    if (props.getBaseUrl() != null && !props.getBaseUrl().isBlank()) {
      return new OpenAiCompatibleClient(
          llmTransport, props.getBaseUrl(), apiKeyProvider, props.getModel());
    }
    return new MockLlmClient();
  }

  /**
   * provider API 密钥解析（按调用，支持轮换 / 短时限 Virtual Key）。
   *
   * <p>2026-10-23 接线：{@link ApiKeyProvider} 端口此前已就位却无任何消费方——客户端构造期固化 {@code String
   * apiKey}，密钥轮换语义落空，「以 {@code @Bean} 覆盖为 bone-security 实现」无从谈起（同 2026-10-21 修
   * {@code LlmMetrics} / {@code ContentSafetyGuard} 零消费方的反模式）。此处补默认实现 + 条件装配：默认从配置
   * 按调用解析，接入方以 {@code @Bean} 覆盖为 bone-security Virtual Key 实现即生效（详设 §6.1）。
   */
  @Bean
  @ConditionalOnMissingBean
  public ApiKeyProvider gatewayApiKeyProvider(LlmGatewayProperties props) {
    return new ConfigApiKeyProvider(props);
  }

  /** 出站传输：出现 Resilience4j registry 即包熔断+重试（§4.1 治理面），否则回落直连（POC 零依赖）。 */
  @Bean
  @ConditionalOnMissingBean
  public LlmHttpTransport llmTransport(
      LlmGatewayProperties props,
      org.springframework.beans.factory.ObjectProvider<CircuitBreakerRegistry> registryProvider) {
    LlmHttpTransport direct = new JdkLlmTransport(props);
    CircuitBreakerRegistry registry = registryProvider.getIfAvailable();
    if (registry != null) {
      return new ResilientLlmTransport(direct, registry);
    }
    return direct;
  }
}
