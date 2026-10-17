package com.bone.aitrace.infrastructure.event;

import com.bone.aitrace.domain.gateway.LlmUsageSink;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 默认计量出口：结构化日志（零 DDL、零新依赖）。
 *
 * <p>用可被日志系统直接索引的 {@code key=value} 形态输出，便于按 {@code tenantId} / {@code model} 聚合出账期口径； 需要
 * Micrometer 打点或聚合表时以 {@code @Bean LlmUsageSink} 覆盖本实现即可。
 */
public class StructuredLoggingLlmUsageSink implements LlmUsageSink {

  private static final Logger log = LoggerFactory.getLogger("com.bone.ai.llm.usage");

  @Override
  public void record(LlmUsageRecord record) {
    log.info(
        "llm_usage tenantId={} model={} promptTokens={} completionTokens={} totalTokens={} traceId={}",
        record.tenantId(),
        record.model(),
        record.promptTokens(),
        record.completionTokens(),
        record.totalTokens(),
        record.traceId());
  }
}
