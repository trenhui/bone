package com.bone.system.adapter.web.dto.resp;

import java.time.LocalDateTime;
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
  private LocalDateTime lastRunAt;
  private LocalDateTime nextRunAt;
  private LocalDateTime createdAt;
  private LocalDateTime updatedAt;
}
