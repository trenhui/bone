package com.bone.iam.application.query.qry;

import lombok.Data;

@Data
public class AccountPageQuery {
  private Integer page = 1;
  private Integer size = 10;
  private String keyword;
  private Integer status;
  private Long tenantId;
}
