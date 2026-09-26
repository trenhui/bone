package com.bone.masterdata.application.query.dto;

import java.time.LocalDateTime;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class MasterDataEntityDTO {
  private Long id;
  private String name;
  private String description;
  private String category;
  private String status;
  private LocalDateTime createdAt;
  private LocalDateTime updatedAt;
  private int fieldCount;

  // G1 收敛 + 三层归属与治理配置（3a 设计 §3.2 / §4.3）
  private String entityCode;
  private Long metaEntityId;
  private String domainCode;
  private Long templateId;
  private String templateVersion;
  private Long owningAppId;
  private String governanceTier;
  private Boolean isVersioning;
  private Boolean workflowEnabled;
}
