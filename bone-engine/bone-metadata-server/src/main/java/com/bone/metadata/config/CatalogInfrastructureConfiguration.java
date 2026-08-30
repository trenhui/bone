package com.bone.metadata.config;

import com.bone.metadata.catalog.domain.gateway.CatalogIdempotencyStore;
import com.bone.metadata.catalog.domain.gateway.TenantProvider;
import com.bone.metadata.catalog.infrastructure.idempotency.InMemoryCatalogIdempotencyStore;
import com.bone.metadata.catalog.infrastructure.idempotency.RedisCatalogIdempotencyStore;
import com.bone.metadata.catalog.infrastructure.tenant.TenantProviderAdapter;
import com.bone.metadata.engine.runtime.RuntimeEntityCatalog;
import com.bone.metadata.runtime.CaffeineCachedRuntimeEntityCatalog;
import com.bone.metadata.runtime.CatalogRuntimeEntityProvider;
import com.bone.metadata.runtime.RedisCachedRuntimeEntityCatalog;
import com.bone.metadata.runtime.RuntimeEntityCacheEvictor;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.data.redis.core.StringRedisTemplate;

@Configuration
@EnableConfigurationProperties(MetadataCatalogProperties.class)
public class CatalogInfrastructureConfiguration {

  @Bean
  @Primary
  @ConditionalOnProperty(
      prefix = "bone.metadata.catalog.idempotency",
      name = "backend",
      havingValue = "redis")
  @ConditionalOnBean(StringRedisTemplate.class)
  public CatalogIdempotencyStore redisCatalogIdempotencyStore(
      StringRedisTemplate redisTemplate,
      ObjectMapper objectMapper,
      MetadataCatalogProperties properties) {
    return new RedisCatalogIdempotencyStore(
        redisTemplate, objectMapper, properties.getIdempotency().getKeyPrefix());
  }

  @Bean
  @ConditionalOnMissingBean(CatalogIdempotencyStore.class)
  public CatalogIdempotencyStore inMemoryCatalogIdempotencyStore() {
    return new InMemoryCatalogIdempotencyStore();
  }

  /** 租户上下文端口装配。替代原静态工具类 {@code CatalogTenantSupport}——改为注入后可 mock， 便于单测驱动多租户场景。 */
  @Bean
  public TenantProvider tenantProviderAdapter() {
    return new TenantProviderAdapter();
  }

  @Bean
  @Primary
  @ConditionalOnProperty(
      prefix = "bone.metadata.catalog.runtime-entity-cache",
      name = "backend",
      havingValue = "redis")
  @ConditionalOnBean(StringRedisTemplate.class)
  public RuntimeEntityCatalog redisCachedRuntimeEntityCatalog(
      CatalogRuntimeEntityProvider delegate,
      StringRedisTemplate redisTemplate,
      ObjectMapper objectMapper,
      MetadataCatalogProperties properties) {
    return new RedisCachedRuntimeEntityCatalog(
        delegate,
        redisTemplate,
        objectMapper,
        properties.getRuntimeEntityCache().getKeyPrefix(),
        properties.getRuntimeEntityCacheTtl());
  }

  @Bean
  @Primary
  @ConditionalOnMissingBean(name = "redisCachedRuntimeEntityCatalog")
  public RuntimeEntityCatalog caffeineCachedRuntimeEntityCatalog(
      CatalogRuntimeEntityProvider delegate, MetadataCatalogProperties properties) {
    return new CaffeineCachedRuntimeEntityCatalog(delegate, properties.getRuntimeEntityCacheTtl());
  }

  @Bean
  @ConditionalOnBean(CaffeineCachedRuntimeEntityCatalog.class)
  public RuntimeEntityCacheEvictor caffeineRuntimeEntityCacheEvictor(
      CaffeineCachedRuntimeEntityCatalog catalog) {
    return catalog;
  }

  @Bean
  @ConditionalOnBean(RedisCachedRuntimeEntityCatalog.class)
  public RuntimeEntityCacheEvictor redisRuntimeEntityCacheEvictor(
      RedisCachedRuntimeEntityCatalog catalog) {
    return catalog;
  }
}
