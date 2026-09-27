package com.bone.masterdata.adapter.web.dto.response;

import java.time.Instant;
import lombok.Builder;
import lombok.Data;

/** 主数据记录版本历史响应（UC-T7 追溯）：隔离领域实体 {@code MasterDataRecordVersion} 的对外暴露面。 */
@Data
@Builder
public class MasterDataRecordVersionResp {
  private Long id;
  private Long recordId;
  private Integer versionNumber;
  private String data;
  private String status;
  private String changeDescription;
  private Long approvedBy;
  private Instant approvedAt;
  private Long createdBy;
  private Instant createdAt;
}
