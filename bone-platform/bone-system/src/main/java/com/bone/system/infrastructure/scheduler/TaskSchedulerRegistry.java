package com.bone.system.infrastructure.scheduler;

import com.bone.metadata.sdk.query.dsl.QueryBuilder;
import com.bone.system.application.port.ScheduleTaskScheduler;
import com.bone.system.domain.schedule.ScheduleTask;
import com.bone.system.domain.schedule.vo.TaskStatus;
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
 * 动态 Cron 定时任务注册器。应用启动后加载 DB 中启用的任务，用 {@link ThreadPoolTaskScheduler} 编程式注册；启停通过 add/remove {@link
 * ScheduledFuture} 实现。
 */
@Slf4j
@Component
public class TaskSchedulerRegistry implements ScheduleTaskScheduler {

  private final Map<Long, ScheduledFuture<?>> futures = new ConcurrentHashMap<>();
  private final ThreadPoolTaskScheduler taskScheduler;
  private final ApplicationContext applicationContext;

  public TaskSchedulerRegistry(
      ThreadPoolTaskScheduler taskScheduler, ApplicationContext applicationContext) {
    this.taskScheduler = taskScheduler;
    this.applicationContext = applicationContext;
  }

  @EventListener(ApplicationReadyEvent.class)
  public void loadEnabledTasks() {
    QueryBuilder.from(ScheduleTask.class).list().stream()
        .filter(t -> t.getStatus() == TaskStatus.ENABLED)
        .forEach(this::register);
    log.info("[ScheduleTask] 已加载 {} 个启用的定时任务", futures.size());
  }

  /** 注册一个任务（内部包装：执行目标 + 记录 lastRunAt）。 */
  @Override
  public void register(ScheduleTask task) {
    cancel(task.getId());
    Runnable runnable = buildRunnable(task);
    ScheduledFuture<?> future = taskScheduler.schedule(runnable, new CronTrigger(task.getCron()));
    futures.put(task.getId(), future);
    log.info("[ScheduleTask] 已注册任务 {} (cron={})", task.getName(), task.getCron());
  }

  /** 注销一个任务。 */
  @Override
  public void cancel(Long taskId) {
    ScheduledFuture<?> future = futures.remove(taskId);
    if (future != null) {
      future.cancel(false);
    }
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
    com.bone.system.domain.repository.ScheduleTaskRepository repo =
        applicationContext.getBean(com.bone.system.domain.repository.ScheduleTaskRepository.class);
    ScheduleTask latest = repo.findById(task.getId());
    if (latest != null) {
      latest.markRun(LocalDateTime.now(), null);
      repo.save(latest);
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
