package com.bone.iam.adapter.web.dto.req;

import lombok.Data;

@Data
public class AssignPermissionReq {
  private Long[] permissionIds;
}
