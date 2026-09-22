package com.bone.system.domain.model.schedule;

import com.bone.core.annotation.Id;
import com.bone.core.domain.AggregateRoot;
import com.bone.core.domain.id.GeneratedValue;
import com.bone.core.domain.id.GenerationStrategy;
import com.bone.metadata.sdk.domain.annotation.Table;
import com.bone.system.domain.model.schedule.vo.TaskStatus;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 系统定时任务聚合。 */
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table("sys_schedule_task")
public class ScheduleTask extends AggregateRoot<Long> {
  @Id
  @GeneratedValue(strategy = GenerationStrategy.DISTRIBUTED_ID)
  private Long id;

  private String name;
  private String cron;
  private String handler;
  private TaskStatus status;
  private LocalDateTime lastRunAt;
  private LocalDateTime nextRunAt;
  private LocalDateTime createdAt;
  private LocalDateTime updatedAt;

  public static ScheduleTask create(
      Long id, String name, String cron, String handler, TaskStatus status) {
    ScheduleTask task = new ScheduleTask();
    task.id = id;
    task.name = name;
    task.cron = cron;
    task.handler = handler;
    task.status = status == null ? TaskStatus.DISABLED : status;
    task.createdAt = LocalDateTime.now();
    task.updatedAt = LocalDateTime.now();
    return task;
  }

  public void update(String name, String cron, String handler) {
    this.name = name;
    this.cron = cron;
    this.handler = handler;
    this.updatedAt = LocalDateTime.now();
  }

  public void enable() {
    this.status = TaskStatus.ENABLED;
    this.updatedAt = LocalDateTime.now();
  }

  public void disable() {
    this.status = TaskStatus.DISABLED;
    this.updatedAt = LocalDateTime.now();
  }

  public void markRun(LocalDateTime runAt, LocalDateTime nextAt) {
    this.lastRunAt = runAt;
    this.nextRunAt = nextAt;
    this.updatedAt = LocalDateTime.now();
  }
}
