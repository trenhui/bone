package com.bone.iam.adapter.web.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class UpdateAccountReq {
  @Email(message = "邮箱格式不正确")
  private String email;

  @Size(max = 20, message = "手机号长度不能超过20")
  private String phone;

  @Size(max = 100, message = "真实姓名长度不能超过100")
  private String realName;

  private Integer status;

  /** 归属部门（主部门）；传 null 且不显式清除时保持原值，语义见应用服务。 */
  private Long deptId;

  private Long[] roleIds;
}
