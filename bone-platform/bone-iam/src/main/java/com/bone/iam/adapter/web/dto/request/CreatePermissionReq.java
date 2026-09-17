package com.bone.iam.adapter.web.dto.request;

import com.bone.iam.domain.permission.vo.PermissionType;
import lombok.Data;

@Data
public class CreatePermissionReq {
  private String code;
  private String name;
  private String description;
  private String resourceType;
  private String resourcePath;
  private String action;
  private Long parentId;
  private PermissionType type;
  private Integer sortOrder;
}
