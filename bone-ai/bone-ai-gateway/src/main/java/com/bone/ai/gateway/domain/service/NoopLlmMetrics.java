package com.bone.ai.gateway.domain.service;

import com.bone.ai.gateway.domain.gateway.LlmMetrics;

/**
 * 默认指标实现（零依赖）：全部发射点为 no-op。
 *
 * <p>未接入度量框架时的安全回退——库形态模块不强制引入 Micrometer / Prometheus。接入方以 {@code @Bean LlmMetrics} 覆盖为
 * 真实适配器即可，热路径埋点无需改动。
 */
public final class NoopLlmMetrics implements LlmMetrics {

  public static final NoopLlmMetrics INSTANCE = new NoopLlmMetrics();

  private NoopLlmMetrics() {}
}
