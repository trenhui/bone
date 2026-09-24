package com.bone.system.infrastructure.scheduler;

import com.bone.system.application.port.out.ScheduleTaskSchedulerPort;
import com.bone.system.common.SystemErrorCodes;
import com.bone.system.common.SystemErrors;
import com.bone.system.domain.model.schedule.ScheduleTask;
import com.bone.system.domain.repository.ScheduleTaskRepository;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledFuture;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationContext;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.scheduling.support.CronTrigger;
import org.springframework.stereotype.Component;

/**
 * 动态 Cron 定时任务注册器——{@link ScheduleTaskSchedulerPort} 的技术实现（出站适配器，E-10.2）。
 *
 * <p>应用启动后加载 DB 中启用的任务，用 {@link ThreadPoolTaskScheduler} 编程式注册；启停通过 add/remove {@link
 * ScheduledFuture} 实现。
 *
 * <p><b>为何放在 {@code infrastructure/scheduler} 而不是 {@code adapter/schedule}</b>：本类是被 application
 * 调用的出站端口实现（方向基础设施 → 外部调度器），而 {@code adapter/schedule} 放的是由 Spring 定时触发的入站任务（如 {@code
 * LogCleanTaskHandler}）。同一种技术按方向分开放， 不以「都叫任务」为由混进一个包（E-10）。
 */
@Slf4j
@Component
public class TaskSchedulerRegistry implements ScheduleTaskSchedulerPort {

  private final Map<Long, ScheduledFuture<?>> futures = new ConcurrentHashMap<>();
  private final ThreadPoolTaskScheduler threadPoolTaskScheduler;
  private final ScheduleTaskRepository scheduleTaskRepository;
  private final ApplicationContext applicationContext;

  public TaskSchedulerRegistry(
      ThreadPoolTaskScheduler threadPoolTaskScheduler,
      ScheduleTaskRepository scheduleTaskRepository,
      ApplicationContext applicationContext) {
    this.threadPoolTaskScheduler = threadPoolTaskScheduler;
    this.scheduleTaskRepository = scheduleTaskRepository;
    this.applicationContext = applicationContext;
  }

  /**
   * 启动后装载启用中的任务。
   *
   * <p>取数走 {@link ScheduleTaskRepository#findEnabled()}：与 HTTP 用例共用同一条 SQL 通道（ADR-0030 SQL
   * 真源唯一），避免「注册中心自己写一句 SELECT」造成第二份查询逻辑。
   */
  @EventListener(ApplicationReadyEvent.class)
  public void loadEnabledTasks() {
    scheduleTaskRepository.findEnabled().forEach(this::register);
    log.info("[ScheduleTask] 已加载 {} 个启用的定时任务", futures.size());
  }

  /** 注册一个任务（内部包装：执行目标 + 记录 lastRunAt）。先注销再注册，保证同一任务只有一个句柄。 */
  @Override
  public void register(ScheduleTask task) {
    cancel(task.getId());
    Runnable runnable = buildRunnable(task);
    ScheduledFuture<?> future =
        threadPoolTaskScheduler.schedule(runnable, new CronTrigger(task.getCron()));
    futures.put(task.getId(), future);
    log.info("[ScheduleTask] 已注册任务 {} (cron={})", task.getName(), task.getCron());
  }

  /** 注销一个任务；未注册时静默返回（幂等）。 */
  @Override
  public void cancel(Long taskId) {
    ScheduledFuture<?> future = futures.remove(taskId);
    if (future != null) {
      future.cancel(false);
    }
  }

  /**
   * 手动立即执行一次：与 CRON 路径跑同一份 handler + 同一条 recordRun 通道，区别只在失败语义—— CRON
   * 路径吞异常只记日志（无人在线等结果），手动路径把「handler 缺失 / 执行失败」抛回调用方。
   */
  @Override
  public long triggerNow(ScheduleTask task) {
    TaskHandler handler = resolveHandler(task.getHandler());
    if (handler == null) {
      throw SystemErrors.of(SystemErrorCodes.SCHEDULE_TASK_HANDLER_NOT_FOUND, task.getHandler());
    }
    long start = System.currentTimeMillis();
    try {
      handler.run(task.getName());
    } finally {
      recordRun(task);
    }
    return System.currentTimeMillis() - start;
  }

  private Runnable buildRunnable(ScheduleTask task) {
    return () -> {
      try {
        TaskHandler handler = resolveHandler(task.getHandler());
        if (handler == null) {
          log.warn("[ScheduleTask] 未找到任务处理器 bean: {}", task.getHandler());
          return;
        }
        handler.run(task.getName());
      } catch (Exception e) {
        log.error("[ScheduleTask] 任务 {} 执行失败", task.getName(), e);
      } finally {
        recordRun(task);
      }
    };
  }

  private void recordRun(ScheduleTask task) {
    ScheduleTask latest = scheduleTaskRepository.findById(task.getId());
    if (latest != null) {
      latest.markRun(LocalDateTime.now(), null);
      scheduleTaskRepository.save(latest);
    }
  }

  private TaskHandler resolveHandler(String beanName) {
    if (beanName == null || beanName.isBlank()) {
      return null;
    }
    try {
      Object bean = applicationContext.getBean(beanName);
      return bean instanceof TaskHandler handler ? handler : null;
    } catch (Exception e) {
      return null;
    }
  }
}
