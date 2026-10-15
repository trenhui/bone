package com.bone.airules.domain.model;

/**
 * 规则执行结果：命中判定 + 表达式追踪路径（判定路径可回放）+ 短路信号。
 *
 * <p>{@code shortCircuit} 语义：规则命中且 {@code hitBlock=true} 时置 {@code RULE_HIT_BLOCK}—— 网关据此短路不调
 * LLM（成本红线结构强制，不依赖调用方自觉）。
 */
public record RuleEvalResult(
    String ruleCode,
    int versionNo,
    boolean hit,
    String tracePath,
    long elapsedMs,
    String shortCircuit) {

  public static RuleEvalResult hit(
      String ruleCode, int versionNo, String tracePath, long elapsedMs, boolean blockLlm) {
    return new RuleEvalResult(
        ruleCode, versionNo, true, tracePath, elapsedMs, blockLlm ? "RULE_HIT_BLOCK" : null);
  }

  public static RuleEvalResult miss(
      String ruleCode, int versionNo, String tracePath, long elapsedMs) {
    return new RuleEvalResult(ruleCode, versionNo, false, tracePath, elapsedMs, null);
  }

  /** 规则命中且配置了命中阻断 ⇒ 调用方（编排 / 网关）应短路，不发起 LLM 调用。 */
  public boolean shouldBlockLlm() {
    return "RULE_HIT_BLOCK".equals(shortCircuit);
  }
}
