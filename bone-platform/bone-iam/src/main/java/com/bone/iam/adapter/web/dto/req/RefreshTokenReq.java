package com.bone.iam.adapter.web.dto.req;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/** 刷新令牌请求 */
@Data
public class RefreshTokenReq {

  @NotBlank(message = "刷新令牌不能为空")
  private String refreshToken;
}
