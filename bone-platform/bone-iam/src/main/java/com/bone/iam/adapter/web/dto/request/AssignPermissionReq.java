package com.bone.iam.adapter.web.dto.request;

import lombok.Data;

@Data
public class AssignPermissionReq {
  private Long[] permissionIds;
}
