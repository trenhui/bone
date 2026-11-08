package com.bone.claim.domain.model.mapping.stp;

/**
 * STP 闸门阈值（主设计 §6.9，保险 STP 率通用口径）。集中常量便于灰度调参，不散落各条件。
 *
 * <p>为什么是这两把尺的阈值：明细级命中率 99.5% 是「单点塌陷」的警戒线（整体 99% 覆盖下某域 0% 覆盖会造成批量人工积压，平均值掩盖单点）；赔案级直通率 95%
 * 是业务痛感发生的维度——一张 200 明细的赔案命中 199 条， 明细级 99.5% 达标但赔案级 0% 整单转人工。两把尺必须同时存在，缺一不可。
 */
public final class StpThresholds {

  /** 明细级命中率下限（%，代理口径见 {@link StpMetrics}）。 */
  public static final double ITEM_HIT_RATE_MIN = 99.5;

  /** 赔案级直通率下限（%，代理口径见 {@link StpMetrics}）。 */
  public static final double CASE_STRAIGHT_THROUGH_RATE_MIN = 95.0;

  /** OPEN 未命中硬条件：必须为零（队列里还有未处置缺口 ⇒ 该域不得自动理算）。 */
  public static final long OPEN_MISS_MAX = 0;

  /** 近 7 天纠错（reopen）事件硬条件：必须为零（reopen=「以为修好其实没修好」，高危信号）。 */
  public static final long CORRECTION_EVENTS_7D_MAX = 0;

  /** 冲突（同保司码出现 >1 个标准码候选）硬条件：必须为零，否则该域禁自动理算。 */
  public static final long CONFLICT_MAX = 0;

  private StpThresholds() {}
}
