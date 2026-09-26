package com.bone.iam.application.query.dto;

import java.time.LocalDateTime;
import lombok.Data;

@Data
public class AccountDTO {
  private Long id;
  private String username;
  private String email;
  private String phone;
  private String realName;
  private String avatarUrl;

  /** 0=禁用, 1=启用, 2=锁定 */
  private Integer status;

  private Boolean isAdmin;
  private Long tenantId;

  /** 归属部门（主部门）；未分配时为 null。 */
  private Long deptId;

  /** 归属部门名称（由部门树装配，避免前端二次查树）。 */
  private String deptName;

  /** 绑定的角色 ID 列表（来自 iam_account_role） */
  private Long[] roleIds;

  private LocalDateTime lastLoginAt;
  private String lastLoginIp;
  private LocalDateTime passwordUpdatedAt;
  private LocalDateTime createdAt;
  private LocalDateTime updatedAt;
}
