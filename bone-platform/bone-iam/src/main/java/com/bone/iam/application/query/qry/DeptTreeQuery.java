package com.bone.iam.application.query.qry;

import lombok.Data;

@Data
public class DeptTreeQuery {
  private Long tenantId;
  private String keyword;
}
