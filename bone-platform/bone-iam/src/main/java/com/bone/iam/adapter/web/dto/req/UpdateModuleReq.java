package com.bone.iam.adapter.web.dto.req;

import lombok.Data;

@Data
public class UpdateModuleReq {
  private String name;
  private String description;
  private Integer status;
}
