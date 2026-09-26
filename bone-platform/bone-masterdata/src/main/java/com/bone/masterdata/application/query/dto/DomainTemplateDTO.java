package com.bone.masterdata.application.query.dto;

import java.time.LocalDateTime;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class DomainTemplateDTO {
  private Long id;
  private String domainCode;
  private String domainName;
  private String description;
  private String currentVersion;
  private String defaultGovernanceTier;
  private String fieldSchema;
  private String ruleSchema;
  private String categorySchema;
  private String status;
  private LocalDateTime createdAt;
  private LocalDateTime updatedAt;
}
