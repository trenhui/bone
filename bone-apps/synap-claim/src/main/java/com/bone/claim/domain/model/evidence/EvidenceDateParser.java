package com.bone.claim.domain.model.evidence;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Optional;

/**
 * 材料日期字段解析（时间轴矛盾判定的前置，R-FRAUD-03 的实际承载）。
 *
 * <p><b>为什么只认白名单格式、认不出就返回空</b>：票据日期一旦猜错，时间轴判定就会把正常案件判成 「药先开票」。而真实 OCR 产物里日期形态极杂（{@code 2026-3-12} /
 * {@code 2026.03.12} / {@code 20260312} / {@code 二〇二六年三月十二日}），任何「尽力而为」的解析都会在某个形态上静默错位一个月或一年。
 * 因此本类只接受两种无歧义形态，其余一律 {@link Optional#empty()}，由调用方落 {@code TIMELINE_DATE_UNRESOLVED}
 * 转人工——<b>不猜，是这条规则的一部分</b>。
 *
 * <p><b>为什么不带时区</b>：票据日期是「当地业务日」的日历日期，不带时刻。把它当 Instant 处理会引入 「UTC 与 Asia/Shanghai 差一天」的错判（跨零点开票的票在
 * UTC 下会落到前一天）。
 */
public final class EvidenceDateParser {

  /** 受支持格式（严格解析：{@code 2026-3-1} 这种非补零写法不接受，避免与「日/月」歧义混淆）。 */
  private static final List<DateTimeFormatter> FORMATS =
      List.of(DateTimeFormatter.ofPattern("uuuu-MM-dd"), DateTimeFormatter.ofPattern("uuuu/MM/dd"));

  private EvidenceDateParser() {}

  /**
   * 解析单个日期字符串。
   *
   * @param raw 原始值（允许前后空白；null / 空白返回空）
   * @return 解析结果；格式不在白名单内返回 {@link Optional#empty()}
   */
  public static Optional<LocalDate> parse(String raw) {
    if (raw == null) {
      return Optional.empty();
    }
    String value = raw.trim();
    if (value.isEmpty()) {
      return Optional.empty();
    }
    for (DateTimeFormatter format : FORMATS) {
      try {
        return Optional.of(LocalDate.parse(value, format));
      } catch (DateTimeParseException ignored) {
        // 换下一种白名单格式继续尝试；全部失败即「认不出」，由调用方转人工。
      }
    }
    return Optional.empty();
  }

  /**
   * 判断两个业务日之间是否存在时间轴矛盾。
   *
   * <p>口径（两条独立的矛盾形态，任一成立即矛盾）：
   *
   * <ul>
   *   <li><b>开票早于就诊</b>：{@code invoiceDate < visitDate}。药/诊疗在就诊之前就已开票，是虚假票据的典型特征；
   *       不设容忍天数——真实业务里「先缴费后就诊」的发票日期与就诊日期同一天，不存在提前。
   *   <li><b>开票明显晚于就诊</b>：{@code invoiceDate - visitDate > maxLagDays}。事后补票常见于跨月补录，
   *       给一个容忍窗口是为了不把正常补录全部打成风险。
   * </ul>
   *
   * @param invoiceDate 发票日期（票据开具日）
   * @param visitDate 就诊日期（出险日）
   * @param maxLagDays 开票晚于就诊的最大容忍天数（含边界；负数按 0 处理，即要求同日）
   */
  public static TimelineVerdict evaluate(
      LocalDate invoiceDate, LocalDate visitDate, int maxLagDays) {
    if (invoiceDate == null || visitDate == null) {
      return TimelineVerdict.UNRESOLVED;
    }
    int tolerance = Math.max(maxLagDays, 0);
    if (invoiceDate.isBefore(visitDate)) {
      return TimelineVerdict.INVOICE_BEFORE_VISIT;
    }
    if (invoiceDate.isAfter(visitDate.plusDays(tolerance))) {
      return TimelineVerdict.INVOICE_TOO_LATE;
    }
    return TimelineVerdict.CONSISTENT;
  }

  /** 时间轴结论（受控枚举，禁自由文本）。 */
  public enum TimelineVerdict {
    /** 一致（含容忍窗口内的晚开票）。 */
    CONSISTENT,
    /** 发票日期早于就诊日期：药先开票。 */
    INVOICE_BEFORE_VISIT,
    /** 发票日期晚于就诊日期超过容忍窗口：事后补票。 */
    INVOICE_TOO_LATE,
    /** 任一日期缺失或无法解析：不猜，转人工。 */
    UNRESOLVED
  }
}
