package com.bone.system.adapter.schedule;

import com.bone.system.domain.repository.ScheduleTaskRepository;
import com.bone.system.infrastructure.scheduler.TaskSchedulerRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * 启动装载 Job：应用就绪后把各租户启用的定时任务注册进进程内调度器。
 *
 * <p><b>为什么必须有这个 Job</b>：{@code TaskSchedulerRegistry} 只负责「注册 / 注销」的技术能力，不知道何时该装载。缺了本Job，重启后 {@link
 * com.bone.system.domain.model.schedule.ScheduleTask} 表里 ENABLED 的任务不会进调度器——启用的定时任务静默不跑，且没有任何报错。
 *
 * <p><b>为什么放在 {@code adapter/schedule} 而不并入 {@code TaskSchedulerRegistry}</b>：装载需要跨租户扫描 {@code
 * sys_schedule_task}， 而架构门禁 {@code all_tenants_scan_only_by_schedule}（E-2/ ADR-0030）只允许 {@code
 * adapter.schedule} 包或登记白名单里的调用方触达 {@code *AllTenants} 入口。 把「何时装载 +
 * 跨租户取数」这一入站时机（adapter）放在本包、这一出站能力（infrastructure）留在 {@code
 * adapter/schedule}、把「如何注册」这一出站能力（infrastructure）留在 {@code
 * TaskSchedulerRegistry}，两边职责都不被稀释，且门禁无需新增豁免条目。
 *
 * <p><b>为何不包 {@code TenantContextRunner}</b>：与 {@code AlertEvaluationJob} 不同，本Job
 * 不做任何写操作，只读全租户启用列表后逐条注册； 注册后的执行与回写各自以「任务自身租户」进入上下文（见 {@code
 * TaskSchedulerRegistry#recordRun}）。装载本身无需租户上下文。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ScheduleTaskLoadJob {

  private final ScheduleTaskRepository scheduleTaskRepository;
  private final TaskSchedulerRegistry taskSchedulerRegistry;

  /** 就绪即装载：早于任何 CRON 触发点，避免重启后首轮漏跑。 */
  @EventListener(ApplicationReadyEvent.class)
  public void loadEnabledTasks() {
    // 跨租户取数只许在 adapter.schedule 发生（E-2 / ArchitectureTest all_tenants_scan_only_by_schedule），
    // 故由本Job 取数后交给注册中心，注册中心自身不再触碰跨租户入口。
    taskSchedulerRegistry.loadEnabledTasks(scheduleTaskRepository.findEnabledAllTenants());
  }
}
