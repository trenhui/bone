package com.bone.aiapp.config;

import com.bone.airag.domain.gateway.RagEventSink;
import com.bone.aitrace.domain.gateway.TenantProvider;
import com.bone.aitrace.domain.repository.EvidenceSnapshotRepository;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * RAG 检索事件落 trace 的装配（R6）：证据仓储存在时把 {@link RagEventSink} 从默认 {@code LoggingRagEventSink} 覆盖为 {@link
 * TraceRagEventSink}。
 *
 * <p><b>为何用 {@code @Configuration + @Bean} 而非 {@code @Component}</b>：{@code @ConditionalOnBean} 在
 * {@code @Component} 上仅在组件扫描后按类型装配，条件求值时机不可靠（依赖仓储由 {@code @EnableSqlRepositories} 早于扫描注册，时序敏感）；
 * {@code @Bean} 方法上的条件在配置解析阶段按序求值，与 {@code TraceAutoConfiguration} 同一模式，稳定。
 *
 * <p><b>覆盖方向</b>：本 Bean 属应用侧用户配置，先于 {@code @AutoConfiguration} 解析——注册后 {@code
 * RagAutoConfiguration.ragEventSink()} 的 {@code @ConditionalOnMissingBean} 即让位；仓储缺失时本 Bean 不装配，
 * 回落默认 {@code LoggingRagEventSink}（检索链零改动，与未接 trace 的历史形态一致）。
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnBean(name = "evidenceSnapshotRepository")
public class RagTraceConfig {

  @Bean
  @ConditionalOnMissingBean(RagEventSink.class)
  public RagEventSink traceRagEventSink(
      EvidenceSnapshotRepository repository, TenantProvider tenantProvider) {
    return new TraceRagEventSink(repository, tenantProvider);
  }
}
