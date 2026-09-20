package com.bone.iam.adapter.web.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/** 授予应用权限请求体：{@code role} ∈ admin / developer / viewer。 */
@Data
public class GrantAppPermissionReq {
  @NotNull private Long userId;
  @NotBlank private String role;
}
