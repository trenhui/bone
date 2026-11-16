package com.bone.masterdata.application.command;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/** 误合并整笔撤销的入站命令。撤销必须具名、有原因，且产生单调版本的补偿事件。 */
@Data
public class RollbackMergeCommand {

  @NotNull private Long tenantId;

  @NotBlank private String mergeId;

  @NotBlank private String reason;

  @NotBlank private String operator;
}
