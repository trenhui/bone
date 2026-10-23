package com.bone.airag.domain.gateway;

import com.bone.airag.domain.model.RagEvent;

/**
 * RAG 检索事件下沉口（降级 / 数据卫生事件；详设 §2.6 P1-10）。
 *
 * <p>检索链不直接依赖任何观测设施——事件经本端口下沉，默认 {@code LoggingRagEventSink}（结构化 WARN），
 * 接入方以 {@code @Bean} 覆盖为 bone-ai-trace 落库 + 告警规则适配（L3 目标态），检索链零改动。
 */
public interface RagEventSink {

  /** 记录一条检索事件（实现方不得抛异常吞掉检索——失败仅记日志，读路径可用性优先）。 */
  void record(RagEvent event);
}
