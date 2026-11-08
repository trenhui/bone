package com.bone.claim.domain.model.inspection;

import java.math.BigDecimal;

/**
 * 后督抽检抽样策略（纯函数，无 IO，可被单测与回放直接复算）。
 *
 * <p><b>为什么用确定性哈希而不是 {@code Math.random()}</b>：抽检比例是监管口径（MVP「结案案件抽检 ≥5%」），必须
 * <b>可复算、可审计</b>——同一批结案案件重复跑调度，抽中集合必须一致，否则「为什么这单被抽到」无法回答，抽样也 无法用于质量指标的趋势对比。
 *
 * <p><b>分层口径</b>（§7.6）：金额段 × 渠道。医院等级需保单快照补列，一期缺列时以 {@code NA} 占位（不臆造维度值）。
 *
 * <p><b>哈希分布</b>：用 SplitMix64 终混（雪崩性好于 {@code claimId % 100}）——自增/雪花 ID 的低位有规律，
 * 直接取模会让「连号案件要么全抽要么全不抽」，分层抽样形同虚设。
 */
public final class InspectionSamplingPolicy {

  /** 抽样精度：万分位（5% → 500）。 */
  private static final int SCALE = 10_000;

  private InspectionSamplingPolicy() {}

  /**
   * 案件是否落入抽检样本。
   *
   * @param claimId 案件ID（抽样键：稳定、与业务无关，避免按金额/客户挑选样本造成抽样偏差）
   * @param ratePercent 抽样比例（百分比，0–100）；0 表示不抽样，100 表示全量（V1.0 100% AI 质检口径）
   */
  public static boolean shouldSample(long claimId, int ratePercent) {
    if (ratePercent <= 0) {
      return false;
    }
    if (ratePercent >= 100) {
      return true;
    }
    long bucket = Math.floorMod(splitMix64(claimId), SCALE);
    return bucket < (long) ratePercent * (SCALE / 100);
  }

  /** 分层抽样标识：{@code 金额段#渠道}，如 {@code 500_1000#WECHAT}。 */
  public static String sampleStratum(BigDecimal claimAmount, String sourceChannel) {
    String band = amountBand(claimAmount);
    String channel =
        sourceChannel == null || sourceChannel.isBlank()
            ? "NA"
            : sourceChannel.trim().toUpperCase(java.util.Locale.ROOT);
    return band + "#" + channel;
  }

  /** 金额段（门诊小额快赔口径，边界左闭右开）。 */
  public static String amountBand(BigDecimal claimAmount) {
    if (claimAmount == null) {
      return "NA";
    }
    if (claimAmount.compareTo(new BigDecimal("500")) < 0) {
      return "LT_500";
    }
    if (claimAmount.compareTo(new BigDecimal("1000")) < 0) {
      return "500_1000";
    }
    if (claimAmount.compareTo(new BigDecimal("5000")) < 0) {
      return "1000_5000";
    }
    return "GE_5000";
  }

  /** SplitMix64 终混（标准常量，纯算术、跨 JVM 一致）。 */
  private static long splitMix64(long seed) {
    long z = seed + 0x9E3779B97F4A7C15L;
    z = (z ^ (z >>> 30)) * 0xBF58476D1CE4E5B9L;
    z = (z ^ (z >>> 27)) * 0x94D049BB133111EBL;
    return z ^ (z >>> 31);
  }
}
