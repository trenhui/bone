package com.bone.iam.application.query.dto;

import java.time.LocalDateTime;
import lombok.Data;

@Data
public class ApplicationDTO {
  private Long id;
  private String name;
  private String code;
  private String description;
  private String icon;
  private Integer status;
  private Integer moduleCount;
  private Integer entityCount;
  private String myRole;
  private LocalDateTime createdAt;
  private LocalDateTime updatedAt;
}
