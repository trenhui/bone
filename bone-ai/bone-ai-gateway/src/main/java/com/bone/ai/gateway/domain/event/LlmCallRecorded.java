package com.bone.ai.gateway.domain.event;

import com.bone.ai.gateway.domain.LlmRequest;
import com.bone.ai.gateway.domain.LlmResponse;
import com.bone.core.domain.DomainEvent;
import java.time.LocalDateTime;

/**
 * LLM 调用已发生事件：由 bone-ai-gateway 在每次真实调用后发布，bone-ai-trace 监听并落证据快照。
 *
 * <p>这是「AI 服务强制经 trace 留痕」的发布端契约——网关只发事件、不感知谁落库，证据链模块经进程内 Spring 事件订阅。
 *
 * <p><b>失败也发布（2026-10-17 复核修复）</b>：provider 超时 / 5xx / 熔断正是事故复盘最需要的证据，设计 §2.1 执行链第 5 步要求
 * 「每次调用（含失败）发布」；此前只发成功，失败调用在证据链里完全不可见。
 *
 * @param traceId 贯穿一次业务动作全链的回放关联键（调用方提供，缺省网关生成）
 * @param request 模型请求
 * @param response 模型响应（失败时为 null）
 * @param errorMessage 失败原因（成功时为 null；失败时 response 为 null）
 * @param occurredAt 发生时刻
 */
public record LlmCallRecorded(
    String traceId,
    LlmRequest request,
    LlmResponse response,
    String errorMessage,
    LocalDateTime occurredAt)
    implements DomainEvent {

  public static LlmCallRecorded of(LlmRequest request, LlmResponse response) {
    return new LlmCallRecorded(
        request.effectiveTraceId(), request, response, null, LocalDateTime.now());
  }

  /** 失败调用也留证（响应对不上 / provider 故障 / 熔断拒绝——事故复盘的直接证据）。 */
  public static LlmCallRecorded ofFailure(LlmRequest request, String errorMessage) {
    return new LlmCallRecorded(
        request.effectiveTraceId(), request, null, errorMessage, LocalDateTime.now());
  }
}
