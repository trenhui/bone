package com.bone.aitrace.config;

import com.bone.aitrace.application.EvidenceQueryApplicationService;
import com.bone.aitrace.domain.gateway.LlmUsageSink;
import com.bone.aitrace.domain.gateway.TenantProvider;
import com.bone.aitrace.domain.repository.EvidenceSnapshotRepository;
import com.bone.aitrace.infrastructure.event.EvidenceTraceListener;
import com.bone.aitrace.infrastructure.event.LlmUsageListener;
import com.bone.aitrace.infrastructure.event.StructuredLoggingLlmUsageSink;
import com.bone.aitrace.infrastructure.gateway.TenantContextProviderAdapter;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;

/**
 * bone-ai-trace 自动配置（Boot 3 {@code .imports} 机制）：注册租户解析 + 证据监听 + 回放查询 + 计量消费。
 *
 * <p>证据快照仓储由使用方通过 {@code @EnableSqlRepositories(basePackages="com.bone.aitrace.domain.repository")}
 * 注册； 监听 / 查询 bean 以 {@code @ConditionalOnBean(EvidenceSnapshotRepository)} 为前提，未接入持久化的上下文不装配（与
 * bone-notification 一致）。
 *
 * <p>计量消费（{@link LlmUsageListener}）<b>不依赖证据仓储</b>——用量记账与证据留痕是两类事实，后者要求 fail-closed（缺租户拒落），
 * 前者只求不丢（尽力而为），故计量 bean 常驻装配、sink 默认落结构化日志。
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

  /** 计量出口（默认结构化日志；接 Micrometer / 聚合表属 L3 变更，届时以 @Bean 覆盖）。 */
  @Bean
  @ConditionalOnMissingBean
  public LlmUsageSink llmUsageSink() {
    return new StructuredLoggingLlmUsageSink();
  }

  @Bean
  @ConditionalOnMissingBean
  public LlmUsageListener llmUsageListener(LlmUsageSink usageSink) {
    return new LlmUsageListener(usageSink);
  }
}
