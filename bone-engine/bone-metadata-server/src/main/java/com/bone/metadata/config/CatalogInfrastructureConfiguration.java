package com.bone.metadata.config;

import com.bone.metadata.catalog.domain.gateway.CatalogIdempotencyStore;
import com.bone.metadata.catalog.domain.gateway.PhysicalStructureGateway;
import com.bone.metadata.catalog.domain.gateway.TenantProvider;
import com.bone.metadata.catalog.domain.repository.IamApplicationRepository;
import com.bone.metadata.catalog.domain.repository.IamModuleRepository;
import com.bone.metadata.catalog.domain.repository.MetaEntityRepository;
import com.bone.metadata.catalog.domain.repository.MetaFieldRepository;
import com.bone.metadata.catalog.domain.service.IamApplicationValidator;
import com.bone.metadata.catalog.domain.service.IamModuleValidator;
import com.bone.metadata.catalog.infrastructure.gateway.JdbcPhysicalStructureGatewayAdapter;
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
import org.springframework.jdbc.core.JdbcTemplate;

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

  /** MVP-11 物理结构对齐网关：按已发布 RUNTIME 实体模型建表/加列（幂等非破坏 DDL）。 */
  @Bean
  public PhysicalStructureGateway physicalStructureGateway(
      JdbcTemplate jdbcTemplate,
      MetaEntityRepository metaEntityRepository,
      MetaFieldRepository metaFieldRepository) {
    return new JdbcPhysicalStructureGatewayAdapter(
        jdbcTemplate, metaEntityRepository, metaFieldRepository);
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

  /**
   * 跨上下文引用一致性守卫：domain 服务不挂 Spring stereotype，由本工厂装配。
   *
   * <p>metadata 上下文不再拥有应用/模块聚合根。写入前需确认所引用的 appId / moduleId 在 IAM 上下文真实存在。这些校验器属领域逻辑，故不放
   * {@code @Component}，改为在此显式构造以保全 domain 零框架依赖。
   */
  @Bean
  public IamApplicationValidator iamApplicationValidator(
      IamApplicationRepository iamApplicationRepository) {
    return new IamApplicationValidator(iamApplicationRepository);
  }

  @Bean
  public IamModuleValidator iamModuleValidator(IamModuleRepository iamModuleRepository) {
    return new IamModuleValidator(iamModuleRepository);
  }
}
