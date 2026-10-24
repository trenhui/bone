package com.bone.ai.gateway.config;

import com.bone.ai.gateway.domain.gateway.TenantQuotaGuard;
import com.bone.ai.gateway.infrastructure.quota.RedisTenantQuotaGuard;
import org.redisson.api.RedissonClient;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

/**
 * Redis 分布式配额装配（多副本部署的配额硬依赖解法，详设 §6.4）。
 *
 * <p><b>触发条件（三条同时满足）</b>：① classpath 有 Redisson（{@code @ConditionalOnClass}——本依赖在 gateway pom 里是
 * {@code optional}，不传递给接入方，接入方自有 Redisson 时才生效，平台其余模块如 metadata-sdk 已在用，非新增依赖暴露面）； ② 容器里存在 {@link
 * RedissonClient} bean（部署配了 Redis 才有）；③ {@code bone.ai.gateway.quota-redis-enabled} 不为 false
 * （默认开——有 Redisson 就该用分布式计数；单副本私有化想强制进程内可显式关）。
 *
 * <p>未满足任一条时静默跳过，配额继续走 {@code LlmGatewayAutoConfiguration} 里的进程内默认实现—— 单副本 / 无 Redis 部署零变化。接入方亦可自建
 * {@code @Bean TenantQuotaGuard} 覆盖（{@code @ConditionalOnMissingBean} 保证不打架）。
 *
 * <p>与 {@link LlmGatewayAutoConfiguration} 的关系：本配置产出 {@link TenantQuotaGuard} bean，被那边的 {@code
 * ObjectProvider<TenantQuotaGuard>} 拾取进入治理策略组——配额开关语义（{@code quota-max-requests-per-window > 0}
 * 才生效）由策略组统一判定，两边不会双建。
 */
@AutoConfiguration
@ConditionalOnClass(RedissonClient.class)
@ConditionalOnProperty(
    prefix = "bone.ai.gateway",
    name = "quota-redis-enabled",
    havingValue = "true",
    matchIfMissing = true)
@EnableConfigurationProperties(LlmGatewayProperties.class)
public class RedisQuotaAutoConfiguration {

  @Bean
  @ConditionalOnBean(RedissonClient.class)
  @ConditionalOnMissingBean(TenantQuotaGuard.class)
  public TenantQuotaGuard redisTenantQuotaGuard(
      RedissonClient redissonClient, LlmGatewayProperties props) {
    return new RedisTenantQuotaGuard(redissonClient, props);
  }
}
