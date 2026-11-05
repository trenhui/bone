package com.bone.claim.domain.model.decision;

/**
 * 落库置信度（{@code claim_decision_execution.ai_confidence}）的来源受控码。
 *
 * <p><b>为什么必须溯源</b>：同一列 {@code ai_confidence} 在不同链路下装的是完全不同性质的数——AI 节点成功时装模型自报 置信度、LLM
 * 降级时装纯规则路径置信度、桩环境时装确定性桩算出来的数。消费方（阈值分诊 {@code AI_CONFIDENCE_LOW}、评测导出、运维看板）此前只能看到一个数值，无法区分「模型说它有
 * 93% 把握」 和「规则引擎给了 0.93 的启发式分」——前者是概率判断，后者是确定性计算，把它们混在一个数里比较， 阈值语义就失真了。
 *
 * <p><b>为什么桩单独成态而不是并入 MODEL</b>：桩置信度是写死的常数，对阈值分诊来说它「永远刚好压线或压不过线」， 若不区分，灰度环境调好的阈值会被桩数据污染（桩永远 0.93 ⇒
 * 阈值 0.9 看起来永远通过）。与 {@link AiModelProvenance} 同一纪律：证明不了是模型，就不许冒充模型。
 *
 * <p><b>NULL 与 UNKNOWN 的区别</b>：{@code confidence_source} 列为 NULL = 历史行（本列加入前落的结论）或
 * 未落结论的执行，语义是「当时没有这个维度」；{@link #UNKNOWN} = 本列加入后落的结论但置信度为 null
 * （如证据缺失短路，根本没走到置信度产生环节）。两者都不是「模型参与过」的证据。
 */
public enum AiConfidenceSource {

  /** 真实模型自报置信度（LLM 节点成功且模型标识可归因到真实上游模型）。 */
  MODEL,

  /** 确定性桩 / 网关替身产生的常数置信度（方法被调用过，没有模型参与）。 */
  STUB,

  /** LLM 降级后取的纯规则路径置信度（{@code ruleConfidence}，确定性计算非概率判断）。 */
  FALLBACK,

  /** 本列加入后落的结论但未产生置信度（如证据缺失短路、能力缺失转人工）。 */
  UNKNOWN;

  /**
   * 由「是否降级 + 模型标识」推导来源（决策图执行器在落结论时调用）。
   *
   * <p>推导规则（与 {@code DecisionGraphExecutor} 的置信度覆写分支一一对应）：
   *
   * <ul>
   *   <li>{@code fallback=true} ⇒ 置信度已被 {@code ruleConfidence} 覆写 ⇒ {@link #FALLBACK}（无论其数值多像模型输出）；
   *   <li>否则模型标识归因为 {@link AiModelProvenance#STUB} ⇒ {@link #STUB}；
   *   <li>否则 LLM 节点成功 ⇒ {@link #MODEL}（「证明不了就不计入参与」的反向：有标识且非替身才给 MODEL， 缺标识按 {@link
   *       AiModelProvenance#UNKNOWN} 判 ⇒ 保守给 {@link #UNKNOWN}，不冒充 MODEL）。
   * </ul>
   */
  public static AiConfidenceSource derive(boolean fallback, String reportedModelId) {
    if (fallback) {
      return FALLBACK;
    }
    AiModelProvenance provenance = AiModelProvenance.of(reportedModelId);
    return switch (provenance) {
      case STUB -> STUB;
      case PROVIDER -> MODEL;
      case UNKNOWN -> UNKNOWN;
    };
  }
}
