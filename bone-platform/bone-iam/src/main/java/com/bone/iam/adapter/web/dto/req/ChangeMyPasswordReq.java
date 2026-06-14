package com.bone.iam.adapter.web.dto.req;

import lombok.Data;

@Data
public class ChangeMyPasswordReq {
  private String oldPassword;
  private String newPassword;
}
