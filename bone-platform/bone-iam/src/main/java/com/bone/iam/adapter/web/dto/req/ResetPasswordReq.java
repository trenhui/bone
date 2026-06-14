package com.bone.iam.adapter.web.dto.req;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class ResetPasswordReq {
  @NotBlank(message = "新密码不能为空")
  @Size(min = 8, max = 128, message = "密码长度必须在8-128之间")
  private String newPassword;
}
