package com.bone.masterdata.application.query.dto;

import java.time.LocalDateTime;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class MasterDataRecordDTO {
  private Long id;
  private Long masterDataEntityId;
  private String data;
  private String status;

  /** 业务唯一编码。 */
  private String recordCode;

  /** 显示名称。 */
  private String displayName;

  /** 生效开始时间。 */
  private LocalDateTime effectiveFrom;

  /** 生效结束时间。 */
  private LocalDateTime effectiveTo;

  /** 是否被新版本取代后仍为当前有效版本。 */
  private Boolean current;

  private LocalDateTime createdAt;
  private LocalDateTime updatedAt;
  private LocalDateTime publishTime;
}
