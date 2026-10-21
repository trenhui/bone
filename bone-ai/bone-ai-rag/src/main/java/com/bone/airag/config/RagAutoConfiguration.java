package com.bone.airag.config;

import com.bone.airag.application.RagApplicationService;
import com.bone.airag.domain.gateway.EmbeddingPort;
import com.bone.airag.domain.gateway.TenantProvider;
import com.bone.airag.domain.repository.ChunkRepository;
import com.bone.airag.infrastructure.embedding.TransitionalHashEmbedding;
import com.bone.airag.infrastructure.gateway.TenantContextProviderAdapter;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;

/**
 * bone-ai-rag 自动配置（Boot 3 {@code .imports} 机制）：注册租户解析 + 嵌入端口 + 检索服务。
 *
 * <p>切片仓储由使用方通过 {@code @EnableSqlRepositories(basePackages="com.bone.airag.domain.repository")}
 * 注册；{@link RagApplicationService} 以 {@code @ConditionalOnBean(ChunkRepository)} 为前提，未接入持久化的上下文不装配。
 */
@AutoConfiguration
public class RagAutoConfiguration {

  /**
   * 租户解析（rag 侧端口）。
   *
   * <p>2026-10-21 更名（原 {@code tenantProvider}）：bone-ai 的 trace / gateway / rag 三个自动配置都注册了名为 {@code
   * tenantProvider} 的 Bean， 而 Spring Boot 3 默认禁止 Bean 定义覆盖（{@code
   * allow-bean-definition-overriding=false}）—— 三者同在一个 classpath（如 bone-ai-app 聚合）即抛 {@code
   * BeanDefinitionOverrideException}，整个上下文起不来。三者端口类型本就互不相同（按类型注入不受影响），此处按模块前缀唯一化。
   */
  @Bean
  @ConditionalOnMissingBean
  public TenantProvider ragTenantProvider() {
    return new TenantContextProviderAdapter();
  }

  @Bean
  @ConditionalOnMissingBean
  public EmbeddingPort embeddingPort() {
    return new TransitionalHashEmbedding();
  }

  @Bean
  @ConditionalOnBean(name = "chunkRepository") // 按名而非按类型：SDK 仓储是 FactoryBean 代理，类型不可静态推断
  @ConditionalOnMissingBean
  public RagApplicationService ragService(
      ChunkRepository chunkRepository, EmbeddingPort embeddingPort, TenantProvider tenantProvider) {
    return new RagApplicationService(chunkRepository, embeddingPort, tenantProvider);
  }
}
