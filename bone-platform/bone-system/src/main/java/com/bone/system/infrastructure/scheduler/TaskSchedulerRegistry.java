package com.bone.system.infrastructure.scheduler;

import com.bone.core.tenant.context.TenantContextRunner;
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
import org.springframework.context.ApplicationContext;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.scheduling.support.CronTrigger;
import org.springframework.stereotype.Component;

/**
 * 动态 Cron 定时任务注册器——{@link ScheduleTaskSchedulerPort} 的技术实现（出站适配器，E-10.2）。
 *
 * <p>提供「装载全部租户的启用任务」与「注册 / 注销单个任务」两个能力；启停通过 add/remove {@link ScheduledFuture}
 * 实现。装载的<b>触发时机</b>由入站侧的 {@code adapter/schedule/ScheduleTaskLoadJob} 持有（本类不监听应用事件）。
 *
 * <p><b>为何放在 {@code infrastructure/scheduler} 而不是 {@code adapter/schedule}</b>：本类是被 application
 * 调用的出站端口实现（方向基础设施 → 外部调度器），而 {@code adapter/schedule} 放的是由 Spring 定时/事件触发的入站任务（如 {@code
 * LogRetentionJob}）。同一种技术按方向分放， 不以「都叫任务」为由混进一个包（E-10）。
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
   * 装载一批已取好的任务，逐条容错。
   *
   * <p><b>为何参数是 {@code Iterable} 而不是「自己去查」</b>：取数必须跨租户（{@code findEnabledAllTenants}）， 而架构门禁 {@code
   * all_tenants_scan_only_by_schedule}（E-2 / ADR-0030）只允许 {@code adapter.schedule} 包调用该入口。 于是「何时装载
   * + 跨租户取数」归入站侧 {@code ScheduleTaskLoadJob}，本类只保留「装载 + 注册」这一技术能力，
   * 不再自己触碰跨租户入口——门禁无需新增豁免条目，职责边界也不被稀释。
   *
   * <p><b>逐条容错</b>：单条任务的脏 cron / 缺失处理器不应让整批装载失败——那会导致本实例上<b>所有</b>租户的 定时任务静默不跑。故失败只记日志并继续。
   */
  public void loadEnabledTasks(Iterable<ScheduleTask> tasks) {
    int failed = 0;
    for (ScheduleTask task : tasks) {
      try {
        register(task);
      } catch (Exception e) {
        failed++;
        log.error("[ScheduleTask] 装载失败，已跳过该任务: id={}, name={}", task.getId(), task.getName(), e);
      }
    }
    log.info("[ScheduleTask] 已加载 {} 个启用的定时任务（跳过 {} 个失败）", futures.size(), failed);
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
      // 整个执行体（含 handler 与回写）都在任务自身租户上下文内：handler 往往读写其他租户表，
      // 只给recordRun 加上下文不足以覆盖 handler 内部。runAs 退出时恢复，线程池复用不串租户。
      TenantContextRunner.runAs(task.getTenantId(), () -> runGuarded(task));
    };
  }

  /** CRON 路径的执行体：吞异常只记日志（无人在线等结果），与手动 triggerNow 的失败语义不同。 */
  private void runGuarded(ScheduleTask task) {
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
  }

  private void recordRun(ScheduleTask task) {
    // 调度线程无租户会话，而sys_schedule_task 是租户作用域表：findById/save 对租户表失败关闭
    // （ADR-0029），不显式声明租户会抛 MissingTenantContextException——且它位于 finally 中，
    // 抛出后每次执行都失败、lastRunAt 永不落库，调度器静默失效。故以「任务自身租户」进入上下文。
    TenantContextRunner.runAs(
        task.getTenantId(),
        () -> {
          ScheduleTask latest = scheduleTaskRepository.findById(task.getId());
          if (latest != null) {
            latest.markRun(LocalDateTime.now(), null);
            scheduleTaskRepository.save(latest);
          }
        });
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
