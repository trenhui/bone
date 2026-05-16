package com.bone.metadata.exception;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
public class ErrorResponse {
  @Schema(description = "错误代码", example = "VALIDATION_FAILED")
  private final String code;

  @Schema(description = "错误描述", example = "字段名不能为空")
  private final String message;

  private final Exception ex;
}
