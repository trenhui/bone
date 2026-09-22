package com.bone.system.application.query.dto;

import com.bone.system.domain.model.schedule.ScheduleTask;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 定时任务的应用投影。 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ScheduleTaskDto {
  private Long id;
  private String name;
  private String cron;
  private String handler;
  private String status;
  private LocalDateTime lastRunAt;
  private LocalDateTime nextRunAt;
  private LocalDateTime createdAt;
  private LocalDateTime updatedAt;

  /** 聚合 → 应用投影。 */
  public static ScheduleTaskDto from(ScheduleTask task) {
    return ScheduleTaskDto.builder()
        .id(task.getId())
        .name(task.getName())
        .cron(task.getCron())
        .handler(task.getHandler())
        .status(task.getStatus().name())
        .lastRunAt(task.getLastRunAt())
        .nextRunAt(task.getNextRunAt())
        .createdAt(task.getCreatedAt())
        .updatedAt(task.getUpdatedAt())
        .build();
  }
}
