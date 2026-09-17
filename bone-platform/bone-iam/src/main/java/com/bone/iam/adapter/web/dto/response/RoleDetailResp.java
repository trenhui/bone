package com.bone.iam.adapter.web.dto.response;

import java.time.LocalDateTime;
import lombok.Data;

@Data
public class RoleDetailResp {
  private Long id;
  private String name;
  private String description;
  private Long tenantId;
  private LocalDateTime createdAt;
  private LocalDateTime updatedAt;
  private Long[] permissionIds;
}
