package com.bone.iam.application.query.dto;

import java.time.LocalDateTime;
import lombok.Data;

@Data
public class DeptDTO {
  private Long id;
  private String name;
  private Long parentId;
  private Integer orderNo;
  private Integer status;
  private Long tenantId;
  private LocalDateTime createdAt;
  private LocalDateTime updatedAt;
}
