package com.bone.system.infrastructure.scheduler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.bone.system.common.SystemErrorCodes;
import com.bone.system.domain.model.schedule.ScheduleTask;
import com.bone.system.domain.model.schedule.valueobject.TaskRunStatus;
import com.bone.system.domain.model.schedule.valueobject.TaskStatus;
import com.bone.system.domain.model.schedule.valueobject.TriggerType;
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
 * {@link TaskSchedulerRegistry} 行为测试：下次执行时间的预排与实算、执行记录落库。
 *
 * <p>用真实 {@link ThreadPoolTaskScheduler}（cron 选在凌晨，测试窗口内不可能触发）而非 mock——注册路径的 {@code planNextRun}
 * 落库依赖真实调度句柄的存在；触发路径走 {@code triggerNow}，与 CRON 共用同一 {@code recordRun} 通道，无需等待真实 cron 到期。
 *
 * <p>流水落库走 {@link ScheduleTaskLogRecorder}（独立事务 bean：手动执行失败会回滚调用方事务，流水必须幸存，见该类 javadoc）。 本测试桩该 bean
 * 验证「记了什么、何时记」；REQUIRES_NEW 的「回滚后仍在」语义由 {@code ScheduleTaskFailedRunRecordTest} 真实事务集成测试担保。
 */
@ExtendWith(MockitoExtension.class)
class TaskSchedulerRegistryTest {

  @Mock ScheduleTaskRepository scheduleTaskRepository;
  @Mock ScheduleTaskLogRecorder scheduleTaskLogRecorder;
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
            threadPoolTaskScheduler,
            scheduleTaskRepository,
            scheduleTaskLogRecorder,
            applicationContext);
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

  /**
   * 手动执行成功必须落一条 SUCCESS 执行记录（触发方式 MANUAL）。
   *
   * <p>判别力：删掉 {@code triggerNow} 成功路径的 {@code recordExecution} 调用即转红—— 那正是「点了立即执行、系统里却不留痕」的缺口。
   */
  @Test
  void triggerNowRecordsSuccessExecution() {
    ScheduleTask task = task("0 0 2 * * ?");
    when(applicationContext.getBean("reportJob")).thenReturn(taskHandler);
    when(scheduleTaskRepository.findById(7L)).thenReturn(task);

    registry.triggerNow(task);

    verify(scheduleTaskLogRecorder)
        .record(
            argThat(
                entry ->
                    entry.getTaskId() == 7L
                        && entry.getTaskName().equals("报表生成")
                        && entry.getTriggerType() == TriggerType.MANUAL
                        && entry.getRunStatus() == TaskRunStatus.SUCCESS
                        && entry.getDurationMs() != null
                        && entry.getDurationMs() >= 0L
                        && entry.getMessage() == null));
  }

  /**
   * 手动执行失败必须落 FAILED 记录且异常照旧抛回（失败语义不被观测设施吞掉）。
   *
   * <p>判别力：{@code triggerNow} 的 catch 块若只抛不记录，{@code verify(scheduleTaskLogRecorder)} 转红。
   */
  @Test
  void triggerNowRecordsFailedExecutionAndRethrows() {
    ScheduleTask task = task("0 0 2 * * ?");
    when(applicationContext.getBean("reportJob")).thenReturn(taskHandler);
    when(scheduleTaskRepository.findById(7L)).thenReturn(task);
    org.mockito.Mockito.doThrow(new IllegalStateException("boom")).when(taskHandler).run("报表生成");

    assertThatThrownBy(() -> registry.triggerNow(task)).isInstanceOf(IllegalStateException.class);

    verify(scheduleTaskLogRecorder)
        .record(
            argThat(
                entry ->
                    entry.getRunStatus() == TaskRunStatus.FAILED
                        && entry.getMessage() != null
                        && entry.getMessage().contains("boom")));
  }

  /** 处理器缺失：落 FAILED 记录（消息指明缺失的 bean）后再抛 HANDLER_NOT_FOUND。 */
  @Test
  void triggerNowRecordsFailedExecutionWhenHandlerMissing() {
    ScheduleTask task = task("0 0 2 * * ?");
    when(applicationContext.getBean("reportJob"))
        .thenThrow(
            new org.springframework.beans.factory.NoSuchBeanDefinitionException("reportJob"));

    assertThatThrownBy(() -> registry.triggerNow(task))
        .isInstanceOf(com.bone.core.exception.BizException.class)
        .hasMessageContaining(SystemErrorCodes.SCHEDULE_TASK_HANDLER_NOT_FOUND);

    verify(scheduleTaskLogRecorder)
        .record(
            argThat(
                entry ->
                    entry.getRunStatus() == TaskRunStatus.FAILED
                        && entry.getMessage() != null
                        && entry.getMessage().contains("reportJob")));
  }
}
