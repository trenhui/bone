package com.bone.iam.adapter.web.dto.response;

import lombok.Data;

/** 审计设置响应 */
@Data
public class AuditSettingsResp {

  /** 日志保留天数 */
  private Integer retentionDays;

  /** 是否启用自动归档 */
  private Boolean autoArchiveEnabled;

  /** 自动归档天数 */
  private Integer archiveAfterDays;

  /** 存储类型：DATABASE/MINIO/S3 */
  private String storageType;

  /** 是否启用WORM保护 */
  private Boolean wormEnabled;
}
