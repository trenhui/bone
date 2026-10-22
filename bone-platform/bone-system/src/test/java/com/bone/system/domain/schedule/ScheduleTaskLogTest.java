package com.bone.system.domain.schedule;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.bone.system.domain.model.schedule.ScheduleTaskLog;
import com.bone.system.domain.model.schedule.valueobject.TaskRunStatus;
import com.bone.system.domain.model.schedule.valueobject.TriggerType;
import java.time.Duration;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;

/** {@link ScheduleTaskLog} 纯单测：快照字段、耗时实算、失败消息（无容器）。 */
class ScheduleTaskLogTest {

  private static final LocalDateTime START = LocalDateTime.of(2026, 10, 22, 2, 0, 0);

  @Test
  void recordCapturesSnapshotAndComputesDuration() {
    LocalDateTime finished = START.plusSeconds(3);

    ScheduleTaskLog log =
        ScheduleTaskLog.record(
            1L,
            1001L,
            7L,
            "报表生成",
            "reportJob",
            TriggerType.CRON,
            TaskRunStatus.SUCCESS,
            START,
            finished,
            null);

    assertEquals(1001L, log.getTenantId());
    assertEquals(7L, log.getTaskId());
    // 快照：任务改名后历史记录仍可读
    assertEquals("报表生成", log.getTaskName());
    assertEquals("reportJob", log.getHandler());
    assertEquals(TriggerType.CRON, log.getTriggerType());
    assertEquals(TaskRunStatus.SUCCESS, log.getRunStatus());
    assertEquals(3000L, log.getDurationMs());
    assertNull(log.getMessage());
  }

  @Test
  void failedRecordKeepsMessage() {
    ScheduleTaskLog log =
        ScheduleTaskLog.record(
            2L,
            1001L,
            7L,
            "报表生成",
            "reportJob",
            TriggerType.MANUAL,
            TaskRunStatus.FAILED,
            START,
            START.plus(Duration.ofMillis(120)),
            "IllegalStateException: boom");

    assertEquals(TaskRunStatus.FAILED, log.getRunStatus());
    assertEquals(TriggerType.MANUAL, log.getTriggerType());
    assertEquals(120L, log.getDurationMs());
    assertEquals("IllegalStateException: boom", log.getMessage());
  }

  @Test
  void unknownRunStatusRejected() {
    assertThrows(
        com.bone.core.exception.DomainException.class, () -> TaskRunStatus.fromString("RUNNING"));
  }
}
