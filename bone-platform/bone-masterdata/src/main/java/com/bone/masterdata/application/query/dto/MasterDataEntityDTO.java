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
}
