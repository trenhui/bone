package com.bone.iam.adapter.web.dto.request;

import lombok.Data;

@Data
public class CreateDeptReq {
  private String name;
  private Long parentId;

  /** 部门负责人账户 ID；可空（小部门可暂不指定）。 */
  private Long leaderId;

  private Integer orderNo;
  private Integer status;
  private Long tenantId;
}
