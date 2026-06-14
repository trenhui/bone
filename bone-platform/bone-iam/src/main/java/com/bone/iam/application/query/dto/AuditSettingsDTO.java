package com.bone.iam.application.query.dto;

import lombok.Data;

/** 审计设置查询结果（应用层 DTO）。 */
@Data
public class AuditSettingsDTO {

  private Integer retentionDays;
  private Boolean autoArchiveEnabled;
  private Integer archiveAfterDays;
  private String storageType;
  private Boolean wormEnabled;
}
