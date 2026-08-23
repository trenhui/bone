package com.bone.system.application.query.dto;

import java.time.LocalDateTime;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class DictDTO {
  private Long id;
  private String type;
  private String typeName;
  private String code;
  private String label;
  private String value;
  private Integer sort;
  private Integer status;
  private LocalDateTime createdAt;
  private LocalDateTime updatedAt;
}
