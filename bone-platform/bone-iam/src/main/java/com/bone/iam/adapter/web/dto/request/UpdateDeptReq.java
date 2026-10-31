package com.bone.iam.adapter.web.dto.request;

import lombok.Data;

@Data
public class UpdateDeptReq {
  private String name;
  private Long parentId;

  /** 部门负责人：null = 不变更，0 = 清空，其他 = 设为该账户。 */
  private Long leaderId;

  private Integer orderNo;
  private Integer status;
}
