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
import org.springframework.scheduling.support.CronExpression;
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
  @Override
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
    // 注册即预排：启用任务后「下次执行时间」在第一次执行前就有值（界面不再空白）。
    // lastRunAt 保持 null——预排不得冒充已执行。
    planNextRun(task);
    Runnable runnable = buildRunnable(task);
    ScheduledFuture<?> future =
        threadPoolTaskScheduler.schedule(runnable, new CronTrigger(task.getCron()));
    futures.put(task.getId(), future);
    log.info("[ScheduleTask] 已注册任务 {} (cron={})", task.getName(), task.getCron());
  }

  /**
   * 预排下次执行时间并落库。
   *
   * <p>调度线程无租户会话，而 {@code sys_schedule_task} 是租户作用域表（save 必须显式声明租户，见 {@link
   * #recordRun}）；故以任务自身租户进入上下文。找不到持久化实例时回退到入参实例，保证至少把预排结果写回自己。
   */
  private void planNextRun(ScheduleTask task) {
    LocalDateTime next = nextRunAtOf(task.getCron(), LocalDateTime.now());
    TenantContextRunner.runAs(
        task.getTenantId(),
        () -> {
          ScheduleTask latest = scheduleTaskRepository.findById(task.getId());
          ScheduleTask target = latest != null ? latest : task;
          target.planNextRun(next);
          scheduleTaskRepository.save(target);
        });
  }

  /**
   * 按 cron 表达式实算 {@code from} 之后的首次触发时间（本地时间视角）。
   *
   * <p>用 {@link CronExpression#next(LocalDateTime)} 而非 {@link
   * CronTrigger#nextExecutionTime}：后者内置「绝不排到过去」 的守卫——会以「当前时刻」为下界持续后推，无法算出任意 {@code from}
   * 之后的下一次；本方法只严格求 {@code from} 之后的首个匹配。
   *
   * <p>非法 / 空 cron 返回 {@code null}（调用方据此决定不预排，而不是抛异常中断整批装载）。
   *
   * @param cron 标准 6 段 Spring cron 表达式
   * @param from 参考时间（通常为当前时间），返回严格晚于该时间的下一次触发
   */
  public static LocalDateTime nextRunAtOf(String cron, LocalDateTime from) {
    if (cron == null || cron.isBlank()) {
      return null;
    }
    try {
      CronExpression expr = CronExpression.parse(cron);
      return expr.next(from);
    } catch (IllegalArgumentException e) {
      return null;
    }
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
          ScheduleTask target = latest != null ? latest : task;
          // 实算下一次触发并落库：lastRunAt=刚跑完，nextRunAt=按 cron 的下一次（不再写死 null）。
          LocalDateTime next = nextRunAtOf(task.getCron(), LocalDateTime.now());
          target.markRun(LocalDateTime.now(), next);
          scheduleTaskRepository.save(target);
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
