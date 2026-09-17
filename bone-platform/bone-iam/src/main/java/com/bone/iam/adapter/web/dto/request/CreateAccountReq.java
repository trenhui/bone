package com.bone.iam.adapter.web.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class CreateAccountReq {
  @NotBlank(message = "用户名不能为空")
  @Size(min = 3, max = 50, message = "用户名长度必须在3-50之间")
  private String username;

  @NotBlank(message = "密码不能为空")
  @Size(min = 8, max = 128, message = "密码长度必须在8-128之间")
  private String password;

  @NotBlank(message = "邮箱不能为空")
  @Email(message = "邮箱格式不正确")
  private String email;

  @Size(max = 20, message = "手机号长度不能超过20")
  private String phone;

  @Size(max = 100, message = "真实姓名长度不能超过100")
  private String realName;

  private Long tenantId;
  private Long[] roleIds;
}
