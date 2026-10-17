package com.bone.aitrace.infrastructure.event;

import com.bone.ai.gateway.domain.event.LlmUsageRecorded;
import com.bone.aitrace.domain.gateway.LlmUsageSink;
import com.bone.aitrace.domain.gateway.LlmUsageSink.LlmUsageRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;

/**
 * LLM 用量计量监听（spend tracking 消费端）。
 *
 * <p><b>补齐的口径（2026-10-17 复核修复）</b>：{@code LlmUsageRecorded} 此前<b>只有发布端、零消费端</b>——事件发了没人收， 「这次调用花了多少
 * token」在生产里查无实据（证据链只留了 prompt/completion 快照，用量随证据一并散落，无法按账期聚合）。 本监听把用量交 {@link
 * LlmUsageSink}，默认落结构化日志；换 Micrometer 打点或聚合表只改 sink 实现。
 *
 * <p><b>为何用 {@code @EventListener} 而非同步监听</b>：与证据链一致（进程内 Spring 事件），保持「网关只发事件、不感知谁消费」的库形态解耦。 <b>为何不
 * fail-closed</b>：与证据链相反——计量失败不应让业务调用抛错（成本记账不是合规留痕，丢一条用量不该阻断业务）； 消费异常只记录告警。
 */
public class LlmUsageListener {

  private static final Logger log = LoggerFactory.getLogger(LlmUsageListener.class);

  private final LlmUsageSink usageSink;

  public LlmUsageListener(LlmUsageSink usageSink) {
    this.usageSink = usageSink;
  }

  @EventListener
  public void onLlmUsageRecorded(LlmUsageRecorded event) {
    try {
      usageSink.record(toRecord(event));
    } catch (RuntimeException e) {
      // 计量是记账，不是合规留痕：记一条用量失败不该让业务调用失败
      log.warn("LLM 用量计量消费失败（不阻断业务）: {}", e.getMessage(), e);
    }
  }

  static LlmUsageRecord toRecord(LlmUsageRecorded event) {
    var usage = event.usage();
    return new LlmUsageRecord(
        event.tenantId(),
        event.model(),
        usage == null ? 0 : usage.promptTokens(),
        usage == null ? 0 : usage.completionTokens(),
        usage == null ? 0 : usage.totalTokens(),
        event.traceId());
  }
}
