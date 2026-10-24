package com.bone.aiapp.config;

import com.bone.ai.gateway.domain.gateway.LlmMetrics;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 可观测装配（2026-10-24 企业复杂场景诊断 P2-1）：宿主存在 {@link MeterRegistry}（Actuator 随附）即以 {@link
 * MicrometerLlmMetrics} 覆盖网关的 Noop 默认。
 *
 * <p>{@code @ConditionalOnClass} 守卫保证库形态宿主（未引 actuator）零影响；{@code @ConditionalOnMissingBean} 保留接入方
 * 以自有适配器（如直发 OTel）覆盖的扩展点。此前 {@code LlmMetrics} 恒为 Noop——AI 成本与质量在生产「盲飞」， 与 enterprise 可观测要求不符（详设
 * §6.2）。
 */
@Configuration
@ConditionalOnClass(MeterRegistry.class)
public class ObservabilityConfiguration {

  @Bean
  @ConditionalOnBean(MeterRegistry.class)
  @ConditionalOnMissingBean(LlmMetrics.class)
  public LlmMetrics micrometerLlmMetrics(MeterRegistry registry) {
    return new MicrometerLlmMetrics(registry);
  }
}
