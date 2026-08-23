package com.bone.masterdata.application.query.dto;

import java.time.LocalDateTime;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class DataStandardDTO {
  private Long id;
  private String entityCode;
  private String fieldCode;
  private Integer ruleType;
  private String pattern;
  private String refCode;
  private String description;
  private LocalDateTime createdAt;
  private LocalDateTime updatedAt;
}
