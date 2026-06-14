package com.bone.iam.adapter.web.dto.req;

import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class UpdateRoleReq {
  @Size(max = 100, message = "角色名称长度不能超过100")
  private String name;

  @Size(max = 500, message = "角色描述长度不能超过500")
  private String description;
}
