package com.bone.masterdata.application.command;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import lombok.Data;

/** 创建分段并行变更请求：三段 specialist 路由由治理组织指定，不由调用方省略。 */
@Data
public class CreateChangeRequestCommand {

  @NotBlank private String changeRequestId;

  @NotNull private Long tenantId;

  @NotBlank private String partyId;

  private long targetVersion;

  @NotBlank private String changeDescription;

  /** 段名 → 具名 Data Steward 账号；必须含 GENERAL / COMPANY_CODE / PURCHASING_ORG 三项。 */
  private Map<String, String> segmentOwners = new LinkedHashMap<>();

  /** 为空时取编排时刻；显式给出用于审计回放确定性。 */
  private Instant createdAt;

  private String createdBy;
}
