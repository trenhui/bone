package com.bone.claim.domain.model.sla;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.DayOfWeek;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;

/**
 * 工作日小时计算（§6.6 时效双轨的 {@code limit_hour} 口径，{@code Asia/Shanghai}，scale=2、HALF_UP）。
 *
 * <p><b>口径</b>：仅周一至周五计入工时，区间内与周末（周六/周日）的实际重叠时长整体剔除；跨周末边界按实际重叠扣除（非只剔"整天"）， 使 {@code limit_hour}
 * 连续且可加。纯函数、零框架依赖，供 SLA 物化与挂起占用计算复用。
 */
public final class WorkHours {

  private static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");
  private static final BigDecimal SECONDS_PER_HOUR = BigDecimal.valueOf(3600);
  private static final BigDecimal ZERO = new BigDecimal("0.00");

  private WorkHours() {}

  /**
   * 区间内的**工作日**小时数（剔除与周末的重叠）。
   *
   * @param start 起点（含），为 {@code null} 或晚于 {@code end} 时返回 0
   * @param end 终点（不含）
   * @return 工作日小时，scale=2、HALF_UP，下限 0
   */
  public static BigDecimal elapsedWorkHours(Instant start, Instant end) {
    if (start == null || end == null || !end.isAfter(start)) {
      return ZERO;
    }
    long totalSeconds = Duration.between(start, end).getSeconds();
    long weekendSeconds = weekendOverlapSeconds(start, end);
    BigDecimal hours =
        BigDecimal.valueOf(totalSeconds - weekendSeconds)
            .divide(SECONDS_PER_HOUR, 2, RoundingMode.HALF_UP);
    return hours.signum() < 0 ? ZERO : hours;
  }

  /** 区间与周末（周六 00:00 至周一 00:00）的重叠秒数。 */
  private static long weekendOverlapSeconds(Instant start, Instant end) {
    LocalDate day = start.atZone(ZONE).toLocalDate();
    LocalDate lastDay = end.atZone(ZONE).toLocalDate();
    long seconds = 0L;
    for (; !day.isAfter(lastDay); day = day.plusDays(1)) {
      if (day.getDayOfWeek() != DayOfWeek.SATURDAY && day.getDayOfWeek() != DayOfWeek.SUNDAY) {
        continue;
      }
      Instant dayStart = day.atStartOfDay(ZONE).toInstant();
      Instant dayEnd = day.plusDays(1).atStartOfDay(ZONE).toInstant();
      Instant overlapStart = start.isAfter(dayStart) ? start : dayStart;
      Instant overlapEnd = end.isBefore(dayEnd) ? end : dayEnd;
      if (overlapEnd.isAfter(overlapStart)) {
        seconds += Duration.between(overlapStart, overlapEnd).getSeconds();
      }
    }
    return seconds;
  }

  /** 把秒数转为小时（scale=2、HALF_UP），供挂起累计秒数换算。 */
  public static BigDecimal secondsToHours(long seconds) {
    if (seconds <= 0) {
      return ZERO;
    }
    return BigDecimal.valueOf(seconds).divide(SECONDS_PER_HOUR, 2, RoundingMode.HALF_UP);
  }

  /** {@code start + hours} 的自然时刻（一期 SLA 到期近似，精确反向工作日历为二期）。 */
  public static Instant plusNaturalHours(Instant start, BigDecimal hours) {
    if (start == null || hours == null) {
      return null;
    }
    return start.plusSeconds(hours.multiply(SECONDS_PER_HOUR).longValue());
  }
}
