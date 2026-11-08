package com.bone.claim.domain.model.config;

import java.util.Locale;

/**
 * 放量定向的作用域维度（G3-3 第二作用域维度）。
 *
 * <p><b>为什么要有第二维度</b>：{@link AiRolloutStage} 解决「放量到哪一档」、{@code rollout_pct} 解决
 * 「同档内抽多大比例」，二者都只在<b>租户</b>这一层。但一次发布往往只想先给「某几个渠道 / 某类机构」 试水，其余渠道维持原状——这正是业界特性开关（LaunchDarkly /
 * Gatekeeper）的 <b>multi-dimensional targeting</b>。单靠租户层无法表达「只在 CH1/CH2 放量」，于是要么全租户一刀切、 要么硬写多个租户开关。
 *
 * <p><b>两种取值</b>：
 *
 * <ul>
 *   <li>{@link #NONE}：不按第二维度切分，整个租户按租户级 {@code stage + rollout_pct} 走（默认，与旧行为逐位一致）；
 *   <li>{@link #CHANNEL}：只在 {@code source_channel ∈ scopeValues} 的案件上放量，其余渠道 fail-closed 转人工。
 * </ul>
 *
 * <p><b>为什么只在 CHANNEL 上做第一版</b>：案件实体当前仅有 {@code source_channel} 这一可信的、决策期可取到的 业务维度（{@code
 * ClaimCase#getSourceChannel()}）；机构 / 险种等维度要么需要跨聚合查询、要么在报案期才稳定。
 * 把维度做成可枚举的封闭集，而非开放的字符串，是为了让「配错维度名」在解析期就回落而不是静默当成 NONE。
 */
public enum AiRolloutScopeDimension {

  /** 不按第二维度切分（整个租户统一放量档）。 */
  NONE,

  /** 仅对白名单内的 {@code source_channel} 放量，其余渠道 fail-closed 转人工。 */
  CHANNEL;

  /**
   * 解析配置值。
   *
   * <p><b>未知值严格回落 {@link #NONE}</b>：与 {@link AiRolloutStage#parse} 同理——猜错维度会让本该
   * 「只对部分渠道放量」的配置退化成「整个租户一刀切」，而回落只损失一次定向机会。{@code null}/空白也回落 NONE。
   *
   * @param raw 配置原文（可为 null / 空白）
   * @return 解析结果，永不返回 null
   */
  public static AiRolloutScopeDimension parse(String raw) {
    if (raw == null || raw.isBlank()) {
      return NONE;
    }
    String normalized = raw.trim().toUpperCase(Locale.ROOT);
    for (AiRolloutScopeDimension dimension : values()) {
      if (dimension.name().equals(normalized)) {
        return dimension;
      }
    }
    return NONE;
  }
}
