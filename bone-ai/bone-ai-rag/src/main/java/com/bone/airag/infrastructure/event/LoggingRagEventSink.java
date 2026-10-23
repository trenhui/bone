package com.bone.airag.infrastructure.event;

import com.bone.airag.domain.gateway.RagEventSink;
import com.bone.airag.domain.model.RagEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 默认事件下沉：结构化 WARN 日志（保持接线前既有可观测性不降级）。
 *
 * <p>目标态：接入方以 {@code @Bean} 覆盖为 bone-ai-trace 落库 + 告警规则适配（详设 §2.6 P1-10，
 * RAG 降级事件纳入 trace 落盘成功率 ≥99.9% 同一条告警线）——端口已就位，无需改检索链。
 */
public class LoggingRagEventSink implements RagEventSink {

  private static final Logger log = LoggerFactory.getLogger(LoggingRagEventSink.class);

  @Override
  public void record(RagEvent event) {
    log.warn("RAG 事件：type={}, detail={}", event.type(), event.detail());
  }
}
