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

  /**
   * 直属主管账户 ID：{@code null} = 不变更，{@code 0} = 清空（无主管），其他 = 设为该账户。
   *
   * <p><b>为什么清空要用 0 哨兵而不是 null</b>：本接口是全列 UPDATE 语义，若 null 表示清空， 任何「只改手机号」的部分提交都会静默把主管清掉。0 不是合法雪花
   * ID，作哨兵无歧义。
   */
  private Long managerId;

  private Long[] roleIds;
}
