package com.bone.claim.application.port.out;

import com.bone.claim.domain.model.decision.AiDegradationReason;

/**
 * AI 网关不可用（运行时，非业务异常）。
 *
 * <p>由 {@link AiGatewayPort#assess} 抛出，决策执行器据此对 LLM 节点降级纯规则路径（FALLBACK）， 而非让整条决策链路失败——fail-safe 的
 * AI 侧表达。
 *
 * <p><b>必须携带受控原因码</b>：降级后决策表只留 {@code FALLBACK}，若异常不区分「没配 provider / 输出不合规 / 超时」，
 * 事后只能人工翻网关日志才能判断该配环境还是该改提示词。原因码随决策轨迹落库（{@code aiUnavailableReason}）， 让「为什么这次没用上 AI」在库里可查。
 */
public class AiGatewayUnavailableException extends RuntimeException {

  private final AiDegradationReason reason;

  /** 兼容旧调用点：未分类一律按 {@link AiDegradationReason#GATEWAY_ERROR} 兜底，不静默丢弃原因维度。 */
  public AiGatewayUnavailableException(String message) {
    this(message, null, AiDegradationReason.GATEWAY_ERROR);
  }

  public AiGatewayUnavailableException(String message, Throwable cause) {
    this(message, cause, AiDegradationReason.GATEWAY_ERROR);
  }

  public AiGatewayUnavailableException(String message, AiDegradationReason reason) {
    this(message, null, reason);
  }

  public AiGatewayUnavailableException(
      String message, Throwable cause, AiDegradationReason reason) {
    super(message, cause);
    this.reason = reason == null ? AiDegradationReason.GATEWAY_ERROR : reason;
  }

  public AiDegradationReason reason() {
    return reason;
  }
}
