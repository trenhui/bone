package com.bone.iam.application.app.query.dto;

import java.time.LocalDateTime;
import lombok.Data;

/** 应用权限视图：{@code role} 为对外小写表示（admin / developer / viewer）。 */
@Data
public class AppPermissionDTO {
  private Long userId;
  private String username;
  private String role;
  private LocalDateTime createdAt;
}
