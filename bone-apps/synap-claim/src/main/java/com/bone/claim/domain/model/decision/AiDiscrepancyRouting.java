package com.bone.claim.domain.model.decision;

import java.util.List;

/**
 * AI 认知 → 非资金流程状态路由（AI 原生契约的关键一步）。
 *
 * <p>v1.3 之前 AI 的结构化认知只进决策轨迹留痕，摘除后主链路不变（L1「AI 辅助」）。 本路由让白名单内的差异提示真正驱动流程状态：<b>仅在本会自动通过时</b>改为补件或复核档，
 * 原因码带具体差异码落 {@code claim_decision_execution.triage_reason}，可回放、可聚合。
 *
 * <p><b>红线不变（P1）</b>：不参与金额、不产拒赔结论、不改理算结果； 只把结论从「自动通过」拉回人工链路，是安全方向的单向收紧。 非 {@code AUTO_APPROVE}
 * 的结论已有更精确的原因码（风险命中 / 大额复核 / 验真未过）， 一律保持原样，不得被 AI 输出覆盖。
 */
public final class AiDiscrepancyRouting {

  public static final String REASON_PREFIX = "AI_DISCREPANCY";

  private AiDiscrepancyRouting() {}

  /**
   * @param rawDiscrepancies AI 节点输出的差异码列表（允许为 null / 非 List，按无差异处理）
   * @param current 确定性分诊结论
   * @return 路由后的结论；无有效差异或无权限收紧时返回原结论
   */
  public static DecisionTriage.Outcome apply(
      Object rawDiscrepancies, DecisionTriage.Outcome current) {
    if (current == null || current.result() != DecisionResult.AUTO_APPROVE) {
      return current;
    }
    List<?> codes = rawDiscrepancies instanceof List<?> list ? list : List.of();
    for (Object item : codes) {
      if (item == null) {
        continue;
      }
      String code = String.valueOf(item);
      if (!AiDiscrepancyCodes.ALL.contains(code)) {
        continue; // 白名单外一律忽略：模型不得用自由文本改变流程
      }
      if (AiDiscrepancyCodes.EVIDENCE_MISSING.equals(code)) {
        return new DecisionTriage.Outcome(
            DecisionResult.NEED_MORE_EVIDENCE, DecisionTier.MANUAL, reason(code));
      }
      return new DecisionTriage.Outcome(
          DecisionResult.MANUAL_REQUIRED, DecisionTier.REVIEW, reason(code));
    }
    return current;
  }

  /** 受控原因码：{@code AI_DISCREPANCY:<码>}，禁自由文本，供审计按差异类型聚合。 */
  public static String reason(String code) {
    return REASON_PREFIX + ":" + code;
  }
}
