package com.bone.masterdata.application.command;

import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import lombok.Data;

@Data
public class ApplyChangeRequestCommand {

  @NotNull private Long tenantId;

  private String changeRequestId;

  /** 应用人不得是提交人或任一审批人（三权分立），由领域内核硬校验。 */
  private String applier;

  private Instant occurredAt;
}
