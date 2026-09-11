package com.bone.system.domain.schedule;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.bone.core.exception.BizException;
import com.bone.system.domain.schedule.vo.TaskStatus;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;

/** {@link ScheduleTask} 纯单测：默认停用、启停切换、任务编辑与运行时间记录（无容器）。 */
class ScheduleTaskTest {

  @Test
  void testCreateDefaultsToDisabled() {
    ScheduleTask task = ScheduleTask.create(1L, "报表生成", "0 0 2 * * ?", "reportJob", null);

    assertEquals(TaskStatus.DISABLED, task.getStatus());
    assertNotNull(task.getName());
    assertNotNull(task.getCreatedAt());
    assertNotNull(task.getUpdatedAt());
  }

  @Test
  void testEnableDisableToggle() {
    ScheduleTask task =
        ScheduleTask.create(2L, "缓存刷新", "0 0/30 * * * ?", "cacheJob", TaskStatus.DISABLED);

    task.enable();
    assertEquals(TaskStatus.ENABLED, task.getStatus());

    task.disable();
    assertEquals(TaskStatus.DISABLED, task.getStatus());
  }

  @Test
  void testUpdateOverridesTaskProfile() {
    ScheduleTask task = ScheduleTask.create(3L, "旧任务", "0 0 1 * * ?", "oldJob", TaskStatus.ENABLED);

    task.update("新任务", "0 0 3 * * ?", "newJob");

    assertEquals("新任务", task.getName());
    assertEquals("0 0 3 * * ?", task.getCron());
    assertEquals("newJob", task.getHandler());
  }

  @Test
  void testMarkRunRecordsExecutionTimestamps() {
    ScheduleTask task =
        ScheduleTask.create(4L, "数据归档", "0 0 2 * * ?", "archiveJob", TaskStatus.ENABLED);
    LocalDateTime runAt = LocalDateTime.of(2026, 9, 5, 2, 0);

    task.markRun(runAt, runAt.plusDays(1));

    assertEquals(runAt, task.getLastRunAt());
    assertEquals(runAt.plusDays(1), task.getNextRunAt());
    assertNotNull(task.getUpdatedAt());
  }

  @Test
  void testUnknownTaskStatusRejected() {
    assertThrows(BizException.class, () -> TaskStatus.fromString("PAUSED"));
  }
}
