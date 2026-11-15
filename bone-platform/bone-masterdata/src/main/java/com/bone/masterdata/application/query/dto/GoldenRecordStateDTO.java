package com.bone.masterdata.application.query.dto;

import java.util.List;
import lombok.Builder;
import lombok.Data;

/** 当前黄金记录的脱敏状态视图：只回字段码与版本，不回字段值（PII 最小化）。 */
@Data
@Builder
public class GoldenRecordStateDTO {
  private String partyId;
  private long version;
  private int fieldCount;
  private List<String> fieldCodes;
}
