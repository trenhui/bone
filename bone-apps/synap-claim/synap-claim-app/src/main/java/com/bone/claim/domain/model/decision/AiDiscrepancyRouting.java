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
 *
 * <p><b>两条确定性要求（v1.7 修正）</b>：
 *
 * <ol>
 *   <li><b>同一组差异恒得同一处置</b>：模型给的数组顺序没有语义，若按「先出现的先赢」路由，同一案件同一组差异
 *       只因模型排列不同就会在「补件」与「复核」之间跳变，审计回放对不上。故主码按 {@link AiDiscrepancyCodes#ALL}
 *       的声明顺序（即处置优先级）选取，与输入顺序无关。
 *   <li><b>原因码必须装得进落库列</b>：{@code triage_reason} 是 {@code VARCHAR(32)}，MySQL 严格模式下超长是 <b>整条语句报 1406
 *       回滚</b>而非静默截断——原因码超宽会让「AI 提示了一条合法差异码」变成「决策执行写不进去」， 在自动通过路径上抛 500。前缀因此取 {@value
 *       #REASON_PREFIX}（短前缀），使最长的 {@code DRUG_INDICATION_MISMATCH} 组成后正好 {@value
 *       AiDiscrepancyCodes#TRIAGE_REASON_MAX_LENGTH} 字符， 由单测逐码断言。
 * </ol>
 */
public final class AiDiscrepancyRouting {

  /**
   * 原因码前缀。
   *
   * <p>曾用 {@code AI_DISCREPANCY}（14 字符），但 {@code AI_DISCREPANCY:DRUG_INDICATION_MISMATCH} 长 39 字符，
   * 超出 {@code triage_reason VARCHAR(32)}，实测写入报 {@code ERROR 1406 Data too long}。缩短前缀是当前唯一 不改 DDL
   * 的收敛办法；若后续把列宽加到 64（DDL 属 L3，需架构师审批），可换回更易读的全称前缀。
   */
  public static final String REASON_PREFIX = "AI_DISC";

  /** 补件结论对应的人工档位：缺件时先补件，不进复核队列。 */
  private static final DecisionResult EVIDENCE_RESULT = DecisionResult.NEED_MORE_EVIDENCE;

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
    String chosen = null;
    for (Object item : codes) {
      if (item == null) {
        continue;
      }
      String code = String.valueOf(item);
      if (!AiDiscrepancyCodes.ALL.contains(code)) {
        continue; // 白名单外一律忽略：模型不得用自由文本改变流程
      }
      // 取声明顺序最靠前的那一个，而不是「数组里先出现的」——同一组差异不同排列必须得到同一结论。
      if (chosen == null
          || AiDiscrepancyCodes.ALL.indexOf(code) < AiDiscrepancyCodes.ALL.indexOf(chosen)) {
        chosen = code;
      }
    }
    if (chosen == null) {
      return current;
    }
    return AiDiscrepancyCodes.EVIDENCE_MISSING.equals(chosen)
        ? new DecisionTriage.Outcome(EVIDENCE_RESULT, DecisionTier.MANUAL, reason(chosen))
        : new DecisionTriage.Outcome(
            DecisionResult.MANUAL_REQUIRED, DecisionTier.REVIEW, reason(chosen));
  }

  /**
   * 受控原因码：{@code AI_DISC:<码>}，禁自由文本，供审计按差异类型聚合。
   *
   * <p>长度受 {@link AiDiscrepancyCodes#TRIAGE_REASON_MAX_LENGTH} 约束，超宽会让决策执行整条写不进去（1406）。
   */
  public static String reason(String code) {
    return REASON_PREFIX + ":" + code;
  }
}
