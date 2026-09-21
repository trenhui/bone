package com.bone.iam.application.command;

import com.bone.iam.domain.permission.vo.PermissionType;
import lombok.Data;

@Data
public class CreatePermissionCommand {
  private String code;
  private String name;
  private String description;
  private String resourceType;
  private String resourcePath;
  private String action;
  private Long parentId;
  private PermissionType type;
  private int sortOrder;
}
