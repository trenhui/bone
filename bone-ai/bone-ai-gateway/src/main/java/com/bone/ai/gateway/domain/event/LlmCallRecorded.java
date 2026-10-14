package com.bone.ai.gateway.domain.event;

import com.bone.ai.gateway.domain.LlmRequest;
import com.bone.ai.gateway.domain.LlmResponse;
import com.bone.core.domain.DomainEvent;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * LLM 调用已发生事件：由 bone-ai-gateway 在每次真实调用后发布，bone-ai-trace 监听并落证据快照。
 *
 * <p>这是「AI 服务强制经 trace 留痕」的发布端契约——网关只发事件、不感知谁落库，证据链模块经进程内 Spring 事件订阅。
 */
public record LlmCallRecorded(
    String traceId, LlmRequest request, LlmResponse response, LocalDateTime occurredAt)
    implements DomainEvent {

  public static LlmCallRecorded of(LlmRequest request, LlmResponse response) {
    return new LlmCallRecorded(
        UUID.randomUUID().toString(), request, response, LocalDateTime.now());
  }
}
