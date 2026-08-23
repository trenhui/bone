package com.bone.iam.adapter.web.dto.req;

import lombok.Data;

@Data
public class UpdateAppReq {
  private String name;
  private String description;
  private String icon;
  private Integer status;
}
