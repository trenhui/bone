package com.bone.system.application.query.dto;

import com.bone.system.domain.model.schedule.ScheduleTaskLog;
import java.time.LocalDateTime;
import lombok.Builder;

/** 定时任务执行记录的应用投影。 */
@Builder
public record ScheduleTaskLogDto(
    Long id,
    Long taskId,
    String taskName,
    String handler,
    String triggerType,
    String runStatus,
    LocalDateTime startedAt,
    LocalDateTime finishedAt,
    Long durationMs,
    String message,
    LocalDateTime createdAt) {

  /** 聚合 → 应用投影。 */
  public static ScheduleTaskLogDto from(ScheduleTaskLog log) {
    return ScheduleTaskLogDto.builder()
        .id(log.getId())
        .taskId(log.getTaskId())
        .taskName(log.getTaskName())
        .handler(log.getHandler())
        .triggerType(log.getTriggerType().name())
        .runStatus(log.getRunStatus().name())
        .startedAt(log.getStartedAt())
        .finishedAt(log.getFinishedAt())
        .durationMs(log.getDurationMs())
        .message(log.getMessage())
        .createdAt(log.getCreatedAt())
        .build();
  }
}
