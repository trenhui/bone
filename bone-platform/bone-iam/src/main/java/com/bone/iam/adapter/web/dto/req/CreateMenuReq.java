package com.bone.iam.adapter.web.dto.req;

import lombok.Data;

@Data
public class CreateMenuReq {
  private String name;
  private Long parentId;
  private String path;
  private String icon;
  private Integer orderNo;
  private String permission;
  private Integer type;
  private Long tenantId;
}
