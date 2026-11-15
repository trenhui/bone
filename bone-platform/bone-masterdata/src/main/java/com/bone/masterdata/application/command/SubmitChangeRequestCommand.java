package com.bone.masterdata.application.command;

import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import lombok.Data;

@Data
public class SubmitChangeRequestCommand {

  @NotNull private Long tenantId;

  private String changeRequestId;

  /** 仅提交人本人可提交；命令里的操作人仍须与登录态/创建人一致。 */
  private String operator;

  private Instant occurredAt;
}
