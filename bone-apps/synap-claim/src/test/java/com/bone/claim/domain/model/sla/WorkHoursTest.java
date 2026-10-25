package com.bone.claim.domain.model.sla;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import org.junit.jupiter.api.Test;

/** 工作日小时计算测试（§6.6 limit_hour 口径：剔除周末重叠，HALF_UP scale=2）。 */
class WorkHoursTest {

  private static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");

  private static Instant at(int year, int month, int day, int hour) {
    return ZonedDateTime.of(year, month, day, hour, 0, 0, 0, ZONE).toInstant();
  }

  @Test
  void countsFullWeekdayHours() {
    // 2026-10-23 为周五，09:00→17:00 = 8h
    assertEquals(
        new BigDecimal("8.00"),
        WorkHours.elapsedWorkHours(at(2026, 10, 23, 9), at(2026, 10, 23, 17)));
  }

  @Test
  void excludesWeekendOverlap() {
    // 周五22:00 → 下周一02:00：合计52h，周六/周日 48h 剔除，剩 4h（周五2h + 周一2h）
    assertEquals(
        new BigDecimal("4.00"),
        WorkHours.elapsedWorkHours(at(2026, 10, 23, 22), at(2026, 10, 26, 2)));
  }

  @Test
  void returnsZeroForInvalidRange() {
    assertEquals(
        new BigDecimal("0.00"),
        WorkHours.elapsedWorkHours(at(2026, 10, 23, 17), at(2026, 10, 23, 9)));
    assertEquals(new BigDecimal("0.00"), WorkHours.elapsedWorkHours(null, at(2026, 10, 23, 9)));
  }

  @Test
  void convertsSecondsToHoursScale2() {
    assertEquals(new BigDecimal("1.00"), WorkHours.secondsToHours(3600));
    assertEquals(new BigDecimal("1.50"), WorkHours.secondsToHours(5400));
    assertEquals(new BigDecimal("0.00"), WorkHours.secondsToHours(0));
  }

  @Test
  void plusNaturalHoursAddsChronologically() {
    Instant start = at(2026, 10, 23, 9);
    assertEquals(at(2026, 10, 23, 13), WorkHours.plusNaturalHours(start, new BigDecimal("4.00")));
  }
}
