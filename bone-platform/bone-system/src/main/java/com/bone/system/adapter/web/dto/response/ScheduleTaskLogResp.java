package com.bone.system.adapter.web.dto.response;

import java.time.Instant;
import lombok.Builder;

/** 定时任务执行记录的对外契约。 */
@Builder
public record ScheduleTaskLogResp(
    Long id,
    Long taskId,
    String taskName,
    String handler,
    String triggerType,
    String runStatus,
    Instant startedAt,
    Instant finishedAt,
    Long durationMs,
    String message,
    Instant createdAt) {}
