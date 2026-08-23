package com.bone.masterdata.application.query.dto;

import java.time.LocalDateTime;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class LineageRecordDTO {
  private Long id;
  private String sourceEntity;
  private String sourceField;
  private String transformType;
  private String targetEntity;
  private String targetField;
  private String schemaName;
  private LocalDateTime createdAt;
}
