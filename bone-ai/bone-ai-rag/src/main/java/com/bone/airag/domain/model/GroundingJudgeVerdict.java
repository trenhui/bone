package com.bone.airag.domain.model;

import java.util.List;

/**
 * 语义接地裁判判定结果（faithfulness 语义层；R5，2026-10-24 落地）。
 *
 * <p>与确定性守卫 {@code GroundingVerdict} 的分工：词面守卫拦「数字不一致 / 无引用 / 引用越界」， 本裁判拦「词面全对但结论上下文没给」的 paraphrase
 * 编造（守卫类注释已如实标注该假阳边界）。两层串联：确定性先跑（零成本），语义裁判后跑（一次模型调用）。
 *
 * <p><b>三态语义（调用方编排的依据）</b>：
 *
 * <ul>
 *   <li>{@code supported=true}（reason={@link #REASON_SUPPORTED}）：放行；
 *   <li>{@code supported=false} 且 reason={@link #REASON_UNSUPPORTED}：裁判明确判定存在无支撑陈述 ⇒
 *       <b>必须拒答</b>（fail-closed，合规红线）；
 *   <li>reason={@link #REASON_UNAVAILABLE}：裁判自身故障（provider 不可用 / 治理拒绝 / 输出不可解析）⇒ <b>不得据此拒答</b>——
 *       裁判是增强层不是唯一防线，因裁判故障把可答的问题变成拒答等于自造可用性事故；调用方回落确定性守卫结论并留降级痕迹。 同样不得据此放行编造——确定性守卫仍在岗。
 * </ul>
 *
 * <p>禁用态（组合根未接入 LLM 裁判）不经过本记录的故障态：{@code NoOpGroundingJudge} 直接返回 {@code supported=true} +
 * reason={@code JUDGE_DISABLED}，语义层零开销。
 *
 * @param supported 答案是否被上下文支撑（false 时须结合 reason 区分「判了不过」与「没判」）
 * @param reason 判定原因码：{@code SUPPORTED} / {@code UNSUPPORTED} / {@code JUDGE_UNAVAILABLE} / {@code
 *     JUDGE_DISABLED}
 * @param unsupportedSentences 被判无支撑的句子（已截断，供审计与回归定位；不可用时为故障摘要位）
 */
public record GroundingJudgeVerdict(
    boolean supported, String reason, List<String> unsupportedSentences) {

  /** 裁判通过：答案陈述均有所选上下文支撑。 */
  public static final String REASON_SUPPORTED = "SUPPORTED";

  /** 裁判明确拒否：存在上下文无法支撑的陈述（paraphrase 编造等词面守卫漏判形态）。 */
  public static final String REASON_UNSUPPORTED = "UNSUPPORTED";

  /**
   * 裁判自身不可用（provider 故障 / 网关治理拒绝 / 输出不可解析）：没判，不是判了不过。
   *
   * <p>与 {@link #REASON_UNSUPPORTED} 的分叉是可用性设计：语义裁判故障不得把「这次答不了」升级成拒答， 也不得静默放行——回落确定性守卫 +
   * 降级痕迹，两种风险都不承担。
   */
  public static final String REASON_UNAVAILABLE = "JUDGE_UNAVAILABLE";

  /** 语义裁判未启用（组合根缺 LLM 裁判实现时的默认态）：恒放行，由确定性守卫独自把关。 */
  public static final String REASON_DISABLED = "JUDGE_DISABLED";

  /** 裁判明确判定存在无支撑陈述（须拒答的唯一条件）。 */
  public boolean rejected() {
    return !supported && REASON_UNSUPPORTED.equals(reason);
  }

  /** 裁判自身不可用（调用方须回落确定性守卫结论并留降级痕迹）。 */
  public boolean unavailable() {
    return REASON_UNAVAILABLE.equals(reason);
  }
}
