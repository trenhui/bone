package com.bone.masterdata.adapter.web.dto.response;

import java.time.Instant;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class MasterDataRecordDetailResp {
  private Long id;
  private Long masterDataEntityId;
  private String data;
  private String status;
  private Integer version;
  private Instant createdAt;
  private Instant updatedAt;
  private Instant publishTime;
}
