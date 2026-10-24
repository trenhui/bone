package com.bone.aiorch.domain.decision;

/**
 * AI 建议（{@code AiSuggestion}）：模型产出的**未经确认**结论载体——AI 边界内允许存在的唯一建议形态。
 *
 * <p><b>为什么需要这个类型</b>：AI 输出是概率性的，直接拿它驱动业务终态等于让模型替业务签字。业界受监管系统 （理赔 / 金融）普遍用<b>类型系统 + 编译期不变量</b>保证「AI
 * 结论不能直接成为业务决定」：建议（{@code AiSuggestion}）与确认（{@link Confirmed}）是两个类型，没有隐式转换路径。
 *
 * <p><b>机械强制（ArchUnit 规则 6）</b>：本类型<b>不得越过 {@code domain.decision} 包边界</b>——应用层 / 适配层 / Web 层禁引用
 * {@code AiSuggestion}，只允许消费 {@link Confirmed}。开发者在编排层把 AI 建议直接落业务表， 编译不过；硬绕（反射 /
 * 字符串拼接）也过不了架构门禁。详见详设 §3.3 规则 6。
 *
 * <p><b>不变量</b>：置信度 ∈ [0,1]；证据引用（{@code evidenceRef}）必填——拿不出证据的建议不是建议，是编造 （与 rag 侧「引用强制
 * fail-closed」同源：无出处内容不配离开 AI 边界）。
 *
 * @param value 建议值（类型由调用方场景决定，平台不感知领域语义）
 * @param confidence 模型自评置信度 0-1（喂给 {@link DecisionGate} 的熔断线之一）
 * @param evidenceRef 证据引用（traceId / 条款锚点等，回放与审计的关联键）
 */
public record AiSuggestion<T>(T value, double confidence, String evidenceRef) {

  public AiSuggestion {
    if (confidence < 0.0 || confidence > 1.0) {
      throw new IllegalArgumentException("AI 建议置信度须在 [0,1]: " + confidence);
    }
    if (evidenceRef == null || evidenceRef.isBlank()) {
      throw new IllegalArgumentException(
          "AI 建议必须携带证据引用（evidenceRef）：无出处的建议不得离开 AI 边界（fail-closed）");
    }
  }

  /**
   * 置信度是否达到自动放行线（默认 0.85，与 {@link DecisionGate#DEFAULT_CONFIDENCE_THRESHOLD} 同源）。
   *
   * <p>便捷判据归类型自身，避免每个调用方各写一遍 {@code s.confidence() >= 0.85} 然后各配一个阈值—— 阈值真源仍在 {@link
   * DecisionGate}（可配、可单测穷举），本方法只服务「无闸门配置」的快速判断。
   */
  public boolean meetsDefaultConfidenceLine() {
    return confidence >= DecisionGate.DEFAULT_CONFIDENCE_THRESHOLD;
  }
}
