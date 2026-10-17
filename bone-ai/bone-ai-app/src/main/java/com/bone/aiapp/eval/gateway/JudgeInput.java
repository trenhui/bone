package com.bone.aiapp.eval.gateway;

/**
 * LLM-as-Judge 输入（系统/判据/输入/期望/实际输出）。
 *
 * @param traceId 全链追踪 ID（透传给网关门面 → 证据快照按同一 ID 回放；为空则网关生成）
 */
public record JudgeInput(
    String system, String criterion, String input, String expected, String actual, String traceId) {

  /** 兼容构造（无显式 traceId）。 */
  public JudgeInput(String system, String criterion, String input, String expected, String actual) {
    this(system, criterion, input, expected, actual, null);
  }
}
