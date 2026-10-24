package com.bone.aiapp.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.bone.airag.config.RagAutoConfiguration;
import com.bone.airag.domain.gateway.RagEventSink;
import com.bone.aitrace.domain.gateway.TenantProvider;
import com.bone.aitrace.domain.repository.EvidenceSnapshotRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

/**
 * {@link RagTraceConfig} 装配测试（R6）：锁死「仓储存在时覆盖为 TraceRagEventSink（唯一）/ 仓储缺失时回落默认 sink」两条接线契约。
 *
 * <p>用 {@link ApplicationContextRunner} 只加载 {@link RagAutoConfiguration} + {@link
 * RagTraceConfig}，不依赖完整生产拓扑， 避免全上下文冒烟测试的环境依赖（本地 .m2 问题），同时验证 Spring 条件装配的真实求值顺序（用户配置先于自动配置）。
 */
class RagTraceConfigTest {

  private final ApplicationContextRunner base =
      new ApplicationContextRunner()
          .withConfiguration(AutoConfigurations.of(RagAutoConfiguration.class))
          .withBean(TenantProvider.class, () -> (TenantProvider) () -> 1L)
          .withUserConfiguration(RagTraceConfig.class);

  @Test
  @DisplayName("证据仓储存在：RagEventSink 唯一且为 TraceRagEventSink（覆盖默认 LoggingRagEventSink）")
  void overrides_default_sink_when_repository_present() {
    base.withBean(
            "evidenceSnapshotRepository",
            EvidenceSnapshotRepository.class,
            () -> mock(EvidenceSnapshotRepository.class))
        .run(
            ctx -> {
              assertThat(ctx).hasSingleBean(RagEventSink.class);
              assertThat(ctx.getBean(RagEventSink.class)).isInstanceOf(TraceRagEventSink.class);
            });
  }

  @Test
  @DisplayName("证据仓储缺失：RagTraceConfig 不装配，回落 rag 默认 LoggingRagEventSink（检索链零改动）")
  void falls_back_to_logging_sink_when_repository_absent() {
    base.run(
        ctx -> {
          assertThat(ctx).hasSingleBean(RagEventSink.class);
          assertThat(ctx.getBean(RagEventSink.class)).isNotInstanceOf(TraceRagEventSink.class);
        });
  }
}
