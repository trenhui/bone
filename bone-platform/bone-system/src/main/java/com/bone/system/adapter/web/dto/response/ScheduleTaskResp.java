package com.bone.system.adapter.web.dto.response;

import java.time.Instant;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class ScheduleTaskResp {
  private Long id;
  private String name;
  private String cron;
  private String handler;
  private String status;
  private Instant lastRunAt;
  private Instant nextRunAt;
  private Instant createdAt;
  private Instant updatedAt;
}
