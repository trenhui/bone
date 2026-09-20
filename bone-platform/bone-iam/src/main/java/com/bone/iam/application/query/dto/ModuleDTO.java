package com.bone.iam.application.query.dto;

import java.time.LocalDateTime;
import lombok.Data;

@Data
public class ModuleDTO {
  private Long id;
  private Long appId;
  private String name;
  private String code;
  private String description;
  private Integer status;
  private Integer entityCount;
  private Integer fieldCount;
  private Integer sortOrder;
  private LocalDateTime createdAt;
}
