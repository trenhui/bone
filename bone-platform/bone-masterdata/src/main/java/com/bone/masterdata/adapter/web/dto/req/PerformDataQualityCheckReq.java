package com.bone.masterdata.adapter.web.dto.req;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

/** 执行数据质量检查请求 */
@Data
public class PerformDataQualityCheckReq {
  @NotNull(message = "主数据实体ID不能为空")
  private Long masterDataEntityId;
}
