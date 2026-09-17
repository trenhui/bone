package com.bone.iam.adapter.web.dto.response;

import java.time.LocalDateTime;
import lombok.Data;

@Data
public class LoginResp {
  private String token;
  private String refreshToken;

  /** 为 true 时表示当前密码为弱口令/默认口令，客户端应引导改密（详设 IAM-14）。 */
  private Boolean requirePasswordChange;

  private AccountInfo account;

  @Data
  public static class AccountInfo {
    private Long id;
    private String username;
    private String email;
    private String phone;
    private String realName;
    private String avatarUrl;
    private Integer status;
    private Boolean isAdmin;
    private LocalDateTime lastLoginAt;
    private LocalDateTime createdAt;
  }
}
