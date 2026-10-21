package com.bone.system.infrastructure.scheduler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.bone.system.domain.model.schedule.ScheduleTask;
import com.bone.system.domain.model.schedule.valueobject.TaskStatus;
import com.bone.system.domain.repository.ScheduleTaskRepository;
import java.time.LocalDateTime;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationContext;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

/**
 * {@link TaskSchedulerRegistry} 行为测试：下次执行时间的预排与实算。
 *
 * <p>用真实 {@link ThreadPoolTaskScheduler}（cron 选在凌晨，测试窗口内不可能触发）而非 mock——注册路径的 {@code planNextRun}
 * 落库依赖真实调度句柄的存在；触发路径走 {@code triggerNow}，与 CRON 共用同一 {@code recordRun} 通道，无需等待真实 cron 到期。
 */
@ExtendWith(MockitoExtension.class)
class TaskSchedulerRegistryTest {

  @Mock ScheduleTaskRepository scheduleTaskRepository;
  @Mock ApplicationContext applicationContext;
  @Mock TaskHandler taskHandler;

  ThreadPoolTaskScheduler threadPoolTaskScheduler;
  TaskSchedulerRegistry registry;

  @BeforeEach
  void setUp() {
    threadPoolTaskScheduler = new ThreadPoolTaskScheduler();
    threadPoolTaskScheduler.initialize();
    registry =
        new TaskSchedulerRegistry(
            threadPoolTaskScheduler, scheduleTaskRepository, applicationContext);
  }

  @AfterEach
  void tearDown() {
    threadPoolTaskScheduler.shutdown();
  }

  private static ScheduleTask task(String cron) {
    return ScheduleTask.create(7L, 1001L, "报表生成", cron, "reportJob", TaskStatus.ENABLED);
  }

  @Test
  void nextRunAtOfComputesNextExecutionFromCron() {
    LocalDateTime from = LocalDateTime.of(2026, 10, 21, 1, 0, 0);

    LocalDateTime next = TaskSchedulerRegistry.nextRunAtOf("0 0 2 * * ?", from);

    assertThat(next).isEqualTo(LocalDateTime.of(2026, 10, 21, 2, 0, 0));
  }

  @Test
  void nextRunAtOfReturnsNullForUnparsableCron() {
    LocalDateTime now = LocalDateTime.now();

    assertThat(TaskSchedulerRegistry.nextRunAtOf("not-a-cron", now)).isNull();
    assertThat(TaskSchedulerRegistry.nextRunAtOf("  ", now)).isNull();
    assertThat(TaskSchedulerRegistry.nextRunAtOf(null, now)).isNull();
  }

  /**
   * 注册即预排：启用任务后、「下次执行时间」在第一次执行前就有值。
   *
   * <p>判别力：删掉 {@code register} 里的 {@code planNextRun} 调用即转红（且断言 {@code lastRunAt ==
   * null}——预排不得冒充已执行）。
   */
  @Test
  void registerPlansNextRunBeforeFirstExecution() {
    ScheduleTask task = task("0 0 2 * * ?");
    when(scheduleTaskRepository.findById(7L)).thenReturn(task);

    registry.register(task);

    verify(scheduleTaskRepository)
        .save(
            argThat(
                saved ->
                    saved.getNextRunAt() != null
                        && saved.getLastRunAt() == null
                        && saved.getNextRunAt().isAfter(LocalDateTime.now().minusSeconds(5))));
  }

  /**
   * 执行后两个时间都落库：lastRunAt=刚跑完，nextRunAt=按 cron 实算的下一次。
   *
   * <p>判别力：{@code recordRun} 若仍写死 {@code markRun(now, null)}，{@code nextRunAt != null} 断言转红。
   */
  @Test
  void triggerNowRecordsBothLastAndNextRun() {
    ScheduleTask task = task("0 0 2 * * ?");
    when(applicationContext.getBean("reportJob")).thenReturn(taskHandler);
    when(scheduleTaskRepository.findById(7L)).thenReturn(task);

    long elapsed = registry.triggerNow(task);

    assertThat(elapsed).isGreaterThanOrEqualTo(0L);
    verify(taskHandler).run("报表生成");
    verify(scheduleTaskRepository)
        .save(
            argThat(
                saved ->
                    saved.getLastRunAt() != null
                        && saved.getNextRunAt() != null
                        && saved.getNextRunAt().isAfter(saved.getLastRunAt())));
  }
}
