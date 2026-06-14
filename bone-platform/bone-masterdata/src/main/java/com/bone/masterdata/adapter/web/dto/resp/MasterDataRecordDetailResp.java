package com.bone.masterdata.adapter.web.dto.resp;

import java.time.LocalDateTime;
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
  private LocalDateTime createdAt;
  private LocalDateTime updatedAt;
  private LocalDateTime publishTime;
}
