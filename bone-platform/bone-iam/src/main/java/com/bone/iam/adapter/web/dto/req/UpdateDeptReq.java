package com.bone.iam.adapter.web.dto.req;

import lombok.Data;

@Data
public class UpdateDeptReq {
  private String name;
  private Long parentId;
  private Integer orderNo;
  private Integer status;
}
