package com.bone.iam.adapter.web.dto.response;

import java.time.Instant;
import lombok.Data;

@Data
public class RoleDetailResp {
  private Long id;
  private String name;
  private String description;
  private Long tenantId;
  private Instant createdAt;
  private Instant updatedAt;
  private Long[] permissionIds;
}
