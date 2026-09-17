package com.bone.iam.adapter.web.dto.request;

import lombok.Data;

@Data
public class CreateDeptReq {
  private String name;
  private Long parentId;
  private Integer orderNo;
  private Integer status;
  private Long tenantId;
}
