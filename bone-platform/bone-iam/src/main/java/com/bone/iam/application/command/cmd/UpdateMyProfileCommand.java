package com.bone.iam.application.command.cmd;

import lombok.Data;

/** 当前账号自助更新昵称/手机/头像。 */
@Data
public class UpdateMyProfileCommand {

  private Long accountId;
  private String realName;
  private String phone;
  private String avatarUrl;
}
