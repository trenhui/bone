package com.bone.claim.domain.model.config;

import java.util.Locale;

/**
 * AI 介入阶段（§11.4 三态开关的资金侧细化，G3 渐进放量）。
 *
 * <p><b>为什么需要一个独立维度，而不是复用 {@code feature_toggle.state}</b>：{@code state} 只有 {@code ENABLED}/{@code
 * DISABLED} 两态，它回答「这个能力开关有没有打开」；而放量阶段回答「打开到什么程度」。 把 {@code AUTOMATED} 写进 {@code state}
 * 列会被读侧当成「关闭」（{@link FeatureToggle#isOn(long)} 只认 {@code ENABLED}），于是「已自动化」静默失效——这正是 {@link
 * FeatureToggle} 类注释里预留的扩展点。
 *
 * <p><b>四档语义（严格单向递增，只能逐级晋升）</b>：
 *
 * <ul>
 *   <li>{@link #SHADOW}：AI 照常计算并留痕，但<b>任何结论都不生效</b>——只用来收集「AI 会怎么判」的对照样本；
 *   <li>{@link #ASSISTED}：AI 建议在工作台可见，<b>由人决策</b>；不自动出金；
 *   <li>{@link #CANARY}：按比例分桶<b>自动出金</b>，未命中的案件仍走人工；
 *   <li>{@link #AUTOMATED}：全量自动出金。
 * </ul>
 *
 * <p><b>{@link #SHADOW} 是默认值</b>（fail-closed）：未配置、配置损坏、配置了不认识的档位，一律回落 SHADOW，
 * 因为「不放量」是唯一不会造成资金损失的默认。
 */
public enum AiRolloutStage {

  /** 只观察不生效（默认档；也是任何配置异常时的兜底档）。 */
  SHADOW(0),

  /** 建议可见、人工决策。 */
  ASSISTED(1),

  /** 按案件分桶小流量自动出金。 */
  CANARY(2),

  /** 全量自动出金。 */
  AUTOMATED(3);

  private final int rank;

  AiRolloutStage(int rank) {
    this.rank = rank;
  }

  /** 序位（用于「只能逐级晋升 / 回滚」的比较；不用 {@code ordinal()} 以免枚举重排后语义漂移）。 */
  public int rank() {
    return rank;
  }

  /**
   * 本档是否允许「不经人工直接出金」。
   *
   * <p>只有 {@link #CANARY} 与 {@link #AUTOMATED} 为 true。这一条是资金侧的唯一判据——{@link #SHADOW} / {@link
   * #ASSISTED} 即使上游所有闸门都通过，也不得自动放款。
   */
  public boolean permitsAutoSettlement() {
    return this == CANARY || this == AUTOMATED;
  }

  /**
   * 本档是否要求模型真实参与（AI 原生 L2/L3「摘除即断流」的分桶判据）。
   *
   * <p>与 {@link #permitsAutoSettlement()} 当前同义，但语义独立：前者管「钱能不能自动出」，本方法管
   * 「模型缺席时还敢不敢让纯规则路径给出自动通过结论」。拆开是为了将来收紧其中一条时不误伤另一条。
   *
   * <p>消费方是 {@code DecisionGraphExecutor}：CANARY / AUTOMATED 下若 LLM 节点 FALLBACK， 不得由规则路径产出
   * AUTO_APPROVE，必须转人工。SHADOW / ASSISTED 保持原有降级行为不变。
   */
  public boolean requiresModelParticipation() {
    return this == CANARY || this == AUTOMATED;
  }

  /** 是否允许计算与留痕（四档都允许；保留方法是为了让「不生效」与「不计算」在代码里不被混淆）。 */
  public boolean permitsEvaluation() {
    return true;
  }

  /**
   * 解析配置值。
   *
   * <p><b>大小写与空白宽松、未知值严格</b>：{@code " canary "} 视为 {@code CANARY}（配置器常见手误，且不会造成 语义歧义）；但{@code
   * "CANARYY"} 这类未知值<b>不猜测</b>，直接回落 {@code fallback}——猜错的代价是「本该灰度却全量」， 而回落只损失一次放量机会。
   *
   * @param raw 配置原文（可为 null / 空白）
   * @param fallback 无法识别时的兜底档（调用方应传 {@link #SHADOW}）
   * @return 解析结果，永不返回 null
   */
  public static AiRolloutStage parse(String raw, AiRolloutStage fallback) {
    if (raw == null || raw.isBlank()) {
      return fallback;
    }
    String normalized = raw.trim().toUpperCase(Locale.ROOT);
    for (AiRolloutStage stage : values()) {
      if (stage.name().equals(normalized)) {
        return stage;
      }
    }
    return fallback;
  }

  /** 下一档（最高档返回自身，避免调用方写出 {@code rank+1} 越界）。 */
  public AiRolloutStage next() {
    for (AiRolloutStage stage : values()) {
      if (stage.rank == this.rank + 1) {
        return stage;
      }
    }
    return this;
  }

  /** 上一档（最低档返回自身）。回滚默认只降一档，而不是直接打回 SHADOW——避免一次抖动让业务彻底停摆。 */
  public AiRolloutStage previous() {
    for (AiRolloutStage stage : values()) {
      if (stage.rank == this.rank - 1) {
        return stage;
      }
    }
    return this;
  }
}
