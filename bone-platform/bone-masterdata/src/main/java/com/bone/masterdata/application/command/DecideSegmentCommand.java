package com.bone.masterdata.application.command;

import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import lombok.Data;

/** 单段裁决入站命令；三段可任意顺序并行提交。 */
@Data
public class DecideSegmentCommand {

  @NotNull private Long tenantId;

  private String changeRequestId;

  private String segment;

  /** true=通过，false=驳回（驳回必须给原因）。 */
  private boolean approved;

  private String approver;

  private String reason;

  private Instant occurredAt;
}
