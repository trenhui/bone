package com.bone.system.application.command.handler;

import com.bone.core.capability.Capability;
import com.bone.core.util.DistributedIdGenerator;
import com.bone.system.application.command.cmd.CreateScheduleTaskCommand;
import com.bone.system.application.command.cmd.DeleteScheduleTaskCommand;
import com.bone.system.application.command.cmd.ToggleScheduleTaskCommand;
import com.bone.system.application.command.cmd.UpdateScheduleTaskCommand;
import com.bone.system.application.port.ScheduleTaskScheduler;
import com.bone.system.common.exception.NotFoundException;
import com.bone.system.domain.repository.ScheduleTaskRepository;
import com.bone.system.domain.schedule.ScheduleTask;
import com.bone.system.domain.schedule.vo.TaskStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Capability(
    name = "ManageScheduleTask",
    description = "定时任务增删改启停",
    inputSchema =
        "{\"name\": \"string\", \"cron\": \"string\", \"handler\": \"string\", \"status\": \"string\"}",
    outputSchema = "{\"taskId\": \"long\"}",
    idempotent = false,
    cost = 1,
    retryable = true,
    timeout = 5)
@Component
@RequiredArgsConstructor
@Transactional
public class ScheduleTaskCommandHandler {
  private final ScheduleTaskRepository scheduleTaskRepository;
  private final ScheduleTaskScheduler taskScheduler;

  public Long create(CreateScheduleTaskCommand cmd) {
    TaskStatus status = TaskStatus.fromString(cmd.getStatus());
    Long id = DistributedIdGenerator.generateLongId();
    ScheduleTask task =
        ScheduleTask.create(id, cmd.getName(), cmd.getCron(), cmd.getHandler(), status);
    scheduleTaskRepository.save(task);
    if (status == TaskStatus.ENABLED) {
      taskScheduler.register(task);
    }
    return task.getId();
  }

  public void update(UpdateScheduleTaskCommand cmd) {
    ScheduleTask task = requireTask(cmd.getId());
    task.update(cmd.getName(), cmd.getCron(), cmd.getHandler());
    scheduleTaskRepository.save(task);
    if (task.getStatus() == TaskStatus.ENABLED) {
      taskScheduler.register(task);
    }
  }

  public void delete(DeleteScheduleTaskCommand cmd) {
    ScheduleTask task = scheduleTaskRepository.findById(cmd.getId());
    if (task != null) {
      taskScheduler.cancel(task.getId());
      scheduleTaskRepository.deleteById(task.getId());
    }
  }

  public void toggle(ToggleScheduleTaskCommand cmd) {
    ScheduleTask task = requireTask(cmd.getId());
    if (cmd.isEnabled()) {
      task.enable();
      scheduleTaskRepository.save(task);
      taskScheduler.register(task);
    } else {
      task.disable();
      scheduleTaskRepository.save(task);
      taskScheduler.cancel(task.getId());
    }
  }

  private ScheduleTask requireTask(Long id) {
    ScheduleTask task = scheduleTaskRepository.findById(id);
    if (task == null) {
      throw new NotFoundException("定时任务不存在: " + id);
    }
    return task;
  }
}
