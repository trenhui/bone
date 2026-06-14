package com.bone.iam.adapter.web.dto.req;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class UpdateTenantReq {
  @Size(max = 200, message = "租户名称长度不能超过200")
  private String name;

  private Integer level;

  @Email(message = "邮箱格式不正确")
  private String adminEmail;
}
