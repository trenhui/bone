package com.bone.aitrace.config;

import com.bone.aitrace.application.EvidenceQueryApplicationService;
import com.bone.aitrace.domain.gateway.TenantProvider;
import com.bone.aitrace.domain.repository.EvidenceSnapshotRepository;
import com.bone.aitrace.infrastructure.event.EvidenceTraceListener;
import com.bone.aitrace.infrastructure.gateway.TenantContextProviderAdapter;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;

/**
 * bone-ai-trace 自动配置（Boot 3 {@code .imports} 机制）：注册租户解析 + 证据监听 + 回放查询。
 *
 * <p>证据快照仓储由使用方通过 {@code @EnableSqlRepositories(basePackages="com.bone.aitrace.domain.repository")}
 * 注册； 监听 / 查询 bean 以 {@code @ConditionalOnBean(EvidenceSnapshotRepository)} 为前提，未接入持久化的上下文不装配（与
 * bone-notification 一致）。
 */
@AutoConfiguration
public class TraceAutoConfiguration {

  @Bean
  @ConditionalOnMissingBean
  public TenantProvider tenantProvider() {
    return new TenantContextProviderAdapter();
  }

  @Bean
  @ConditionalOnBean(EvidenceSnapshotRepository.class)
  @ConditionalOnMissingBean
  public EvidenceTraceListener evidenceTraceListener(
      EvidenceSnapshotRepository repository, TenantProvider tenantProvider) {
    return new EvidenceTraceListener(repository, tenantProvider);
  }

  @Bean
  @ConditionalOnBean(EvidenceSnapshotRepository.class)
  @ConditionalOnMissingBean
  public EvidenceQueryApplicationService evidenceQueryService(
      EvidenceSnapshotRepository repository) {
    return new EvidenceQueryApplicationService(repository);
  }
}
