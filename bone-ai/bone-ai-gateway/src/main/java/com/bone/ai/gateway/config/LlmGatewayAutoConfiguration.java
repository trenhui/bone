package com.bone.ai.gateway.config;

import com.bone.ai.gateway.application.LlmGatewayApplicationService;
import com.bone.ai.gateway.domain.LlmClient;
import com.bone.ai.gateway.domain.LlmGatewayPolicies;
import com.bone.ai.gateway.domain.gateway.LlmResponseCache;
import com.bone.ai.gateway.domain.gateway.PiiRedactor;
import com.bone.ai.gateway.domain.gateway.StructuredOutputValidator;
import com.bone.ai.gateway.domain.gateway.TenantProvider;
import com.bone.ai.gateway.domain.gateway.TenantQuotaGuard;
import com.bone.ai.gateway.infrastructure.LlmHttpTransport;
import com.bone.ai.gateway.infrastructure.MockLlmClient;
import com.bone.ai.gateway.infrastructure.OpenAiCompatibleClient;
import com.bone.ai.gateway.infrastructure.ResilientLlmTransport;
import com.bone.ai.gateway.infrastructure.ResilientLlmTransport.RetriableHttpException;
import com.bone.ai.gateway.infrastructure.cache.InMemoryLlmResponseCache;
import com.bone.ai.gateway.infrastructure.event.SpringDomainEventPublisher;
import com.bone.ai.gateway.infrastructure.gateway.TenantContextProviderAdapter;
import com.bone.ai.gateway.infrastructure.quota.InMemoryTenantQuotaGuard;
import com.bone.ai.gateway.infrastructure.security.JsonOutputValidator;
import com.bone.ai.gateway.infrastructure.security.RegexPiiRedactor;
import com.bone.core.domain.event.DomainEventPublisher;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
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
      LlmGatewayPolicies policies) {
    return new LlmGatewayApplicationService(
        llmClient, domainEventPublisher, tenantProvider, policies);
  }

  /**
   * 治理策略组（脱敏 / 输出校验 / 缓存 / 配额）。
   *
   * <p>各项均可按配置关闭；默认开启脱敏与输出校验（合规默认），缓存与配额默认关闭（需按部署容量显式开启）。
   */
  @Bean
  @ConditionalOnMissingBean
  public LlmGatewayPolicies gatewayPolicies(
      LlmGatewayProperties props,
      ObjectProvider<PiiRedactor> piiRedactor,
      ObjectProvider<StructuredOutputValidator> validator,
      ObjectProvider<LlmResponseCache> cache,
      ObjectProvider<TenantQuotaGuard> quota) {
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
    return new LlmGatewayPolicies(redactor, outputValidator, responseCache, quotaGuard);
  }

  @Bean
  @ConditionalOnMissingBean
  public DomainEventPublisher domainEventPublisher(
      ApplicationEventPublisher applicationEventPublisher) {
    return new SpringDomainEventPublisher(applicationEventPublisher);
  }

  /** 租户解析（计量事件用；application 层经端口取租户，不直连 TenantContext）。 */
  @Bean
  @ConditionalOnMissingBean
  public TenantProvider tenantProvider() {
    return new TenantContextProviderAdapter();
  }

  @Bean
  @ConditionalOnMissingBean
  public LlmClient llmClient(LlmHttpTransport llmTransport, LlmGatewayProperties props) {
    if (props.getBaseUrl() != null && !props.getBaseUrl().isBlank()) {
      return new OpenAiCompatibleClient(
          llmTransport, props.getBaseUrl(), props.getApiKey(), props.getModel());
    }
    return new MockLlmClient();
  }

  /** 出站传输：出现 Resilience4j registry 即包熔断+重试（§4.1 治理面），否则回落直连（POC 零依赖）。 */
  @Bean
  @ConditionalOnMissingBean
  public LlmHttpTransport llmTransport(
      LlmGatewayProperties props,
      org.springframework.beans.factory.ObjectProvider<CircuitBreakerRegistry> registryProvider) {
    LlmHttpTransport direct = jdkTransport(props);
    CircuitBreakerRegistry registry = registryProvider.getIfAvailable();
    if (registry != null) {
      return new ResilientLlmTransport(direct, registry);
    }
    return direct;
  }

  private static LlmHttpTransport jdkTransport(LlmGatewayProperties props) {
    Duration timeout = props.getTimeout() != null ? props.getTimeout() : Duration.ofSeconds(30);
    HttpClient http = HttpClient.newBuilder().connectTimeout(timeout).build();
    return (url, headers, body) -> {
      HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(url)).timeout(timeout);
      for (Map.Entry<String, String> h : headers.entrySet()) {
        builder.header(h.getKey(), h.getValue());
      }
      builder.POST(HttpRequest.BodyPublishers.ofString(body));
      try {
        HttpResponse<String> resp =
            http.send(builder.build(), HttpResponse.BodyHandlers.ofString());
        if (resp.statusCode() >= 500) {
          throw new RetriableHttpException("LLM 服务端错误: HTTP " + resp.statusCode(), null);
        }
        if (resp.statusCode() < 200 || resp.statusCode() >= 300) {
          throw new IllegalStateException("LLM 调用失败: HTTP " + resp.statusCode());
        }
        return resp.body();
      } catch (java.io.IOException e) {
        // 仅 InterruptedException 需要恢复中断标记；IOException 误设中断位会污染调用方线程状态
        throw new RetriableHttpException("LLM 调用异常: " + e.getMessage(), e);
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        throw new RetriableHttpException("LLM 调用被中断: " + e.getMessage(), e);
      }
    };
  }
}
