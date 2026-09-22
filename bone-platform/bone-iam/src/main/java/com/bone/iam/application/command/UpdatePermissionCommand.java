package com.bone.iam.application.command;

import com.bone.iam.domain.model.permission.valueobject.PermissionType;
import lombok.Data;

@Data
public class UpdatePermissionCommand {
  private Long id;
  private String name;
  private String description;
  private String resourceType;
  private String resourcePath;
  private String action;
  private Long parentId;
  private PermissionType type;
  private Integer sortOrder;
}
