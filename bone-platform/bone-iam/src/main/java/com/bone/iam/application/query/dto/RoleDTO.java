package com.bone.iam.application.query.dto;

import java.time.LocalDateTime;
import lombok.Data;

@Data
public class RoleDTO {
  private Long id;
  private String name;
  private String description;
  private Long tenantId;
  private LocalDateTime createdAt;
  private LocalDateTime updatedAt;
}
