package com.bone.system.application;

import com.bone.core.capability.Capability;
import com.bone.core.domain.event.DomainEventPublisher;
import com.bone.core.exception.BizException;
import com.bone.core.model.PageResult;
import com.bone.core.util.DistributedIdGenerator;
import com.bone.system.application.command.CreateScheduleTaskCommand;
import com.bone.system.application.command.UpdateScheduleTaskCommand;
import com.bone.system.application.port.out.ScheduleTaskSchedulerPort;
import com.bone.system.application.query.dto.ScheduleTaskDto;
import com.bone.system.application.query.qry.ScheduleTaskPageQuery;
import com.bone.system.common.SystemErrorCodes;
import com.bone.system.common.SystemErrors;
import com.bone.system.domain.model.schedule.ScheduleTask;
import com.bone.system.domain.model.schedule.valueobject.TaskStatus;
import com.bone.system.domain.repository.ScheduleTaskRepository;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 定时任务用例入口（写 + 读 + 调度注册同一入口，ADR-0028）。
 *
 * <p><b>出站端口的边界</b>：真正驱动 {@code ThreadPoolTaskScheduler} 的是 {@link ScheduleTaskSchedulerPort}
 * ——application 只声明「注册 / 注销」这两个用例需要的能力，不见 Spring 调度器也不见基础设施包（CORE-02 依赖倒置）。
 *
 * <p><b>注册为什么紧跟在写之后</b>：任务启用与否是业务事实落在库里，注册只是把这个事实推给调度器；反过来先注册再落库， 会在 crash 时留下「跑了但库里没启用」的幽灵任务。本类的
 * {@code @Transactional} 保证二者同进退——这也是不把注册挪去异步消息的理由（否则要用 Outbox 重新造一遍同样的保证）。
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ScheduleTaskApplicationService {

  private final ScheduleTaskRepository scheduleTaskRepository;
  private final ScheduleTaskSchedulerPort scheduleTaskSchedulerPort;
  private final DomainEventPublisher domainEventPublisher;

  @Capability(
      name = "ManageScheduleTask",
      description = "定时任务增删改启停",
      inputSchema =
          "{\"name\": \"string\", \"cron\": \"string\", \"handler\": \"string\", \"status\":"
              + " \"string\"}",
      outputSchema = "{\"taskId\": \"long\"}",
      idempotent = false,
      cost = 1,
      retryable = true,
      timeout = 5)
  @Transactional
  public Long create(CreateScheduleTaskCommand command) {
    TaskStatus status = parseTaskStatus(command.getStatus());
    ScheduleTask task =
        ScheduleTask.create(
            DistributedIdGenerator.generateLongId(),
            command.getName(),
            command.getCron(),
            command.getHandler(),
            status);
    scheduleTaskRepository.save(task);
    domainEventPublisher.publishFrom(task);
    if (status == TaskStatus.ENABLED) {
      scheduleTaskSchedulerPort.register(task);
    }
    return task.getId();
  }

  @Transactional
  public void update(UpdateScheduleTaskCommand command) {
    ScheduleTask task = requireTask(command.getId());
    task.update(command.getName(), command.getCron(), command.getHandler());
    scheduleTaskRepository.save(task);
    domainEventPublisher.publishFrom(task);
    syncScheduler(task);
  }

  @Transactional
  public void toggle(Long id, boolean enabled) {
    ScheduleTask task = requireTask(id);
    if (enabled) {
      task.enable();
    } else {
      task.disable();
    }
    scheduleTaskRepository.save(task);
    domainEventPublisher.publishFrom(task);
    syncScheduler(task);
  }

  @Transactional
  public void delete(Long id) {
    ScheduleTask task = scheduleTaskRepository.findById(id);
    if (task == null) {
      return;
    }
    scheduleTaskSchedulerPort.cancel(id);
    scheduleTaskRepository.deleteById(id);
  }

  /**
   * 手动立即执行一次任务（不改启停状态、不占 CRON 排期）。
   *
   * <p>执行发生在当前事务内且同步等待——「点一下、等结果」的用例语义；处理器失败包装为 {@code SCHEDULE_TASK_RUN_FAILED} 抛回，执行耗时返回给调用方做反馈。
   */
  @Transactional
  public long runNow(Long id) {
    ScheduleTask task = requireTask(id);
    try {
      return scheduleTaskSchedulerPort.triggerNow(task);
    } catch (BizException e) {
      throw e;
    } catch (Exception e) {
      log.error("[ScheduleTask] 手动执行任务 {} 失败", task.getName(), e);
      throw SystemErrors.of(SystemErrorCodes.SCHEDULE_TASK_RUN_FAILED, task.getName(), e);
    }
  }

  public Optional<ScheduleTaskDto> getById(Long id) {
    return Optional.ofNullable(scheduleTaskRepository.findById(id)).map(ScheduleTaskDto::from);
  }

  public PageResult<ScheduleTaskDto> page(ScheduleTaskPageQuery query) {
    PageResult<ScheduleTask> page =
        scheduleTaskRepository.pageByKeywordAndStatus(
            query.getKeyword(),
            parseTaskStatusOrNull(query.getStatus()),
            query.getPageNum(),
            query.getPageSize());
    return PageResult.of(
        page.getRecords().stream().map(ScheduleTaskDto::from).toList(),
        page.getTotal(),
        page.getPage(),
        page.getSize());
  }

  /**
   * 把「任务当前是否启用」这一业务事实同步给调度器：启用则（重新）注册，停用则注销。
   *
   * <p>重复注册是安全的——端口实现内部先 {@code cancel} 再注册，保证同一 id 只有一个 {@code ScheduledFuture}。
   */
  private void syncScheduler(ScheduleTask task) {
    if (task.getStatus() == TaskStatus.ENABLED) {
      scheduleTaskSchedulerPort.register(task);
    } else {
      scheduleTaskSchedulerPort.cancel(task.getId());
    }
  }

  private ScheduleTask requireTask(Long id) {
    ScheduleTask task = scheduleTaskRepository.findById(id);
    if (task == null) {
      throw SystemErrors.of(SystemErrorCodes.SCHEDULE_TASK_NOT_FOUND, id);
    }
    return task;
  }

  private static TaskStatus parseTaskStatus(String value) {
    try {
      return TaskStatus.fromString(value);
    } catch (RuntimeException ex) {
      throw SystemErrors.of(SystemErrorCodes.SCHEDULE_TASK_STATUS_INVALID, value);
    }
  }

  private static TaskStatus parseTaskStatusOrNull(String value) {
    return value == null || value.isBlank() ? null : parseTaskStatus(value);
  }
}
