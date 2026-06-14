package com.bone.iam.adapter.web.dto.req;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class CreateTenantReq {
  @NotBlank(message = "租户名称不能为空")
  @Size(max = 200, message = "租户名称长度不能超过200")
  private String name;

  @NotBlank(message = "租户编码不能为空")
  @Size(max = 50, message = "租户编码长度不能超过50")
  private String code;

  private Integer level;

  @NotBlank(message = "管理员邮箱不能为空")
  @Email(message = "邮箱格式不正确")
  private String adminEmail;
}
