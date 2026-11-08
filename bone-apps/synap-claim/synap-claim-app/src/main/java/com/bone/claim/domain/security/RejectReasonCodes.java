package com.bone.claim.domain.security;

import java.util.List;
import java.util.Set;

/**
 * 拒赔原因码白名单（AI 输出结构化校验之四，配套 {@link LlmOutputGuard} 字段白名单思路）。
 *
 * <p>不利决定（拒赔）须可解释且可审计：模型只允许在 {@code rejectSuggested=true} 时回传本白名单内的<b>枚举码</b>，
 * 绝不允许自由文本理由（防臆造、防注入式理由）。不在白名单内的码一律丢弃（fail-closed）， 既保证"拒赔有原因"可追溯，又不让模型主导理由措辞。
 *
 * <p>与《业务规则书（Rulebook）》拒赔条款对齐；新增拒赔条款时须在此登记，避免 AI 输出未登记码。
 */
public final class RejectReasonCodes {

  private RejectReasonCodes() {}

  /** 证据不足以支撑赔付（缺关键材料 / 材料未通过校验）。 */
  public static final String EVIDENCE_INSUFFICIENT = "EVIDENCE_INSUFFICIENT";

  /** 临床合理性未通过（与医学目录 / 诊疗规范冲突）。 */
  public static final String CLINICAL_UNREASONABLE = "CLINICAL_UNREASONABLE";

  /** 命中保单责任免除条款（免责期 / 非保障范围）。 */
  public static final String POLICY_EXCLUSION = "POLICY_EXCLUSION";

  /** 赔付金额与事实 / 票据不符（金额不一致）。 */
  public static final String AMOUNT_MISMATCH = "AMOUNT_MISMATCH";

  /** 命中反欺诈规则（疑似欺诈 / 异常模式）。 */
  public static final String FRAUD_SUSPECTED = "FRAUD_SUSPECTED";

  /** 前述枚举均不适用时的兜底码（仍须人工复核给出具体原因）。 */
  public static final String OTHER = "OTHER";

  /** 允许模型回传的全部拒赔原因码。 */
  public static final Set<String> ALLOWED =
      Set.of(
          EVIDENCE_INSUFFICIENT,
          CLINICAL_UNREASONABLE,
          POLICY_EXCLUSION,
          AMOUNT_MISMATCH,
          FRAUD_SUSPECTED,
          OTHER);

  /** 单次拒赔最多携带的原因码数量（避免理由膨胀、聚焦主因）。 */
  public static final int MAX_REASONS = 3;

  /**
   * 归一化模型回传的原因码：仅保留白名单内码、去重、截断至上限；空 / 非列表 / 全非法 → 返回 {@link #OTHER}。
   *
   * <p>未知码直接丢弃（绝不信任模型臆造的理由措辞），但保留"至少有兜底原因"以保证可解释性。
   */
  public static List<String> normalize(List<?> raw) {
    if (raw == null || raw.isEmpty()) {
      return List.of(OTHER);
    }
    java.util.LinkedHashSet<String> kept = new java.util.LinkedHashSet<>();
    for (Object o : raw) {
      if (o == null) {
        continue;
      }
      String s = o.toString().trim();
      if (ALLOWED.contains(s)) {
        kept.add(s);
      }
      if (kept.size() >= MAX_REASONS) {
        break;
      }
    }
    return kept.isEmpty() ? List.of(OTHER) : List.copyOf(kept);
  }
}
