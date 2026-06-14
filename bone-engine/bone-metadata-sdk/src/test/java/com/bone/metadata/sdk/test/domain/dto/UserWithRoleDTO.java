package com.bone.metadata.sdk.test.domain.dto;

import java.util.Date;
import lombok.Data;

@Data
public class UserWithRoleDTO {
  private Long id;
  private String name;
  private Long roleId;
  private String roleName;
  private String roleDescription;
  private Date createdAt;
  private Long createdBy;
  private Date updatedAt;
  private Long updatedBy;
  private Boolean deleted;
}
