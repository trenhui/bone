package com.bone.ai.gateway.config;

import com.bone.ai.gateway.application.LlmGatewayService;
import com.bone.ai.gateway.domain.LlmClient;
import com.bone.ai.gateway.infrastructure.LlmHttpTransport;
import com.bone.ai.gateway.infrastructure.MockLlmClient;
import com.bone.ai.gateway.infrastructure.OpenAiCompatibleClient;
import com.bone.ai.gateway.infrastructure.event.SpringDomainEventPublisher;
import com.bone.core.domain.event.DomainEventPublisher;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;
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
 */
@AutoConfiguration
@EnableConfigurationProperties(LlmGatewayProperties.class)
public class LlmGatewayAutoConfiguration {

  @Bean
  @ConditionalOnMissingBean
  public LlmGatewayService llmGatewayService(
      LlmClient llmClient, DomainEventPublisher domainEventPublisher) {
    return new LlmGatewayService(llmClient, domainEventPublisher);
  }

  @Bean
  @ConditionalOnMissingBean
  public DomainEventPublisher domainEventPublisher(
      ApplicationEventPublisher applicationEventPublisher) {
    return new SpringDomainEventPublisher(applicationEventPublisher);
  }

  @Bean
  @ConditionalOnMissingBean
  public LlmClient llmClient(LlmGatewayProperties props) {
    if (props.getBaseUrl() != null && !props.getBaseUrl().isBlank()) {
      return new OpenAiCompatibleClient(
          jdkTransport(props), props.getBaseUrl(), props.getApiKey(), props.getModel());
    }
    return new MockLlmClient();
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
        if (resp.statusCode() < 200 || resp.statusCode() >= 300) {
          throw new IllegalStateException("LLM 调用失败: HTTP " + resp.statusCode());
        }
        return resp.body();
      } catch (java.io.IOException | InterruptedException e) {
        Thread.currentThread().interrupt();
        throw new IllegalStateException("LLM 调用异常: " + e.getMessage(), e);
      }
    };
  }
}
