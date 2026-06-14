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
  private LocalDateTime createdAt;
  private LocalDateTime updatedAt;
  private LocalDateTime publishTime;
}
