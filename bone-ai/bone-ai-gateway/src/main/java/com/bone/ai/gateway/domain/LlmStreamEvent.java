package com.bone.ai.gateway.domain;

/**
 * 流式补全事件：调用方按类型模式匹配处理。
 *
 * <ul>
 *   <li>{@link Token}：一段增量文本（已脱敏），逐片下发；
 *   <li>{@link Done}：流正常结束，携带最终模型 / 结束原因 / 用量；
 *   <li>{@link Error}：流中断（provider 错误、治理拦截），消费方应停止并兜底。
 * </ul>
 *
 * <p>消费方约定：{@code Error} 与 {@code Done} 互斥，至多一个出现且为流的最后事件；{@code Token} 可零到多片。
 */
public sealed interface LlmStreamEvent
    permits LlmStreamEvent.Token, LlmStreamEvent.Done, LlmStreamEvent.Error {

  /** 一段增量文本（门面已做出站脱敏）。 */
  record Token(String delta) implements LlmStreamEvent {}

  /** 流结束（含最终模型 / 结束原因 / 用量）。 */
  record Done(String model, String finishReason, LlmUsage usage) implements LlmStreamEvent {}

  /** 流错误（provider 错误或治理拦截）。 */
  record Error(String message, Throwable cause) implements LlmStreamEvent {}

  /** 构造一段增量事件。 */
  static Token token(String delta) {
    return new Token(delta);
  }

  /** 构造流结束事件。 */
  static Done done(String model, String finishReason, LlmUsage usage) {
    return new Done(model, finishReason, usage);
  }

  /** 构造流错误事件。 */
  static Error error(String message, Throwable cause) {
    return new Error(message, cause);
  }
}
