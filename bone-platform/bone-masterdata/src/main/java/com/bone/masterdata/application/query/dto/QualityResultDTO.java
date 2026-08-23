package com.bone.masterdata.application.query.dto;

import java.time.LocalDateTime;
import lombok.Builder;
import lombok.Data;

/** 质量检查结果 DTO（对齐前端 qualityResultApi 的 DataQualityResult）。 */
@Data
@Builder
public class QualityResultDTO {
  private Long id;
  private Long masterDataRecordId;
  private Long dataQualityRuleId;
  private boolean passed;
  private String message;
  private LocalDateTime timestamp;
}
