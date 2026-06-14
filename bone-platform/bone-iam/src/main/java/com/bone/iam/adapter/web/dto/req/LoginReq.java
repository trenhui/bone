package com.bone.iam.adapter.web.dto.req;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class LoginReq {
  @NotBlank(message = "用户名不能为空")
  private String username;

  private String account;

  @NotBlank(message = "密码不能为空")
  private String password;

  public String getUsername() {
    return username != null ? username : account;
  }
}
