package com.bone.iam.application.query.dto;

import java.time.LocalDateTime;
import lombok.Data;

@Data
public class MenuDTO {
  private Long id;
  private String name;
  private Long parentId;
  private String path;
  private String icon;
  private Integer orderNo;
  private String permission;
  private Integer type;
  private Long tenantId;
  private LocalDateTime createdAt;
  private LocalDateTime updatedAt;
}
