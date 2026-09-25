package com.bone.iam.adapter.web.dto.response;

import lombok.Data;

/** 创建租户响应：租户 id + 自动初始化的租户管理员登录信息（详设 §2.9）。 */
@Data
public class CreateTenantResp {
  private Long tenantId;

  /** 初始化生成的租户管理员账号 id。 */
  private Long adminAccountId;

  /** 租户管理员登录用户名（{@code <code>_admin}）。 */
  private String adminUsername;

  /** 一次性初始密码：仅本次响应可见，明文不落库；客户端应提示平台管理员转交租户。 */
  private String initialPassword;
}
