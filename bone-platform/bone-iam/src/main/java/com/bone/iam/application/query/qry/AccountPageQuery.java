package com.bone.iam.application.query.qry;

import lombok.Data;

@Data
public class AccountPageQuery {
  private Integer page = 1;
  private Integer size = 10;
  private String keyword;
  private Integer status;
  private Long tenantId;

  /** 按部门过滤：命中该部门及其所有后代部门的成员（子树语义，与组织树点击行为一致）。 */
  private Long deptId;
}
