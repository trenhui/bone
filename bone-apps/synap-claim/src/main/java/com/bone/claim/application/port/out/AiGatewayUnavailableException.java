package com.bone.claim.application.port.out;

/**
 * AI 网关不可用（运行时，非业务异常）。
 *
 * <p>由 {@link AiGatewayPort#assess} 抛出，决策执行器据此对 LLM 节点降级纯规则路径（FALLBACK）， 而非让整条决策链路失败——fail-safe 的
 * AI 侧表达。
 */
public class AiGatewayUnavailableException extends RuntimeException {

  public AiGatewayUnavailableException(String message) {
    super(message);
  }

  public AiGatewayUnavailableException(String message, Throwable cause) {
    super(message, cause);
  }
}
