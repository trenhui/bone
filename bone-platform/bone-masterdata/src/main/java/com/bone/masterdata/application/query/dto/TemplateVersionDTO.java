package com.bone.masterdata.application.query.dto;

import java.time.LocalDateTime;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class TemplateVersionDTO {
  private Long id;
  private Long templateId;
  private String versionNumber;
  private String changeLog;
  private String fieldSchema;
  private String ruleSchema;
  private String categorySchema;
  private LocalDateTime createdAt;
}
