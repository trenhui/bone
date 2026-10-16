package com.bone.airag.config;

import com.bone.airag.application.RagService;
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
 * 注册；{@link RagService} 以 {@code @ConditionalOnBean(ChunkRepository)} 为前提，未接入持久化的上下文不装配。
 */
@AutoConfiguration
public class RagAutoConfiguration {

  @Bean
  @ConditionalOnMissingBean
  public TenantProvider tenantProvider() {
    return new TenantContextProviderAdapter();
  }

  @Bean
  @ConditionalOnMissingBean
  public EmbeddingPort embeddingPort() {
    return new TransitionalHashEmbedding();
  }

  @Bean
  @ConditionalOnBean(ChunkRepository.class)
  @ConditionalOnMissingBean
  public RagService ragService(
      ChunkRepository chunkRepository, EmbeddingPort embeddingPort, TenantProvider tenantProvider) {
    return new RagService(chunkRepository, embeddingPort, tenantProvider);
  }
}
