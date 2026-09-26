package com.bone.iam.adapter.web.dto.response;

import java.time.Instant;
import lombok.Data;

@Data
public class AccountDetailResp {
  private Long id;
  private String username;
  private String email;
  private String phone;
  private String realName;
  private String avatarUrl;
  private Integer status;
  private Boolean isAdmin;

  /** 归属部门（主部门）。 */
  private Long deptId;

  private String deptName;

  private Long[] roleIds;
  private Instant lastLoginAt;
  private String lastLoginIp;
  private Instant createdAt;
  private Instant updatedAt;
}
