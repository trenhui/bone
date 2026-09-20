package com.bone.iam.application.query.qry;

import lombok.Data;

@Data
public class ModuleListQuery {
  private Long appId;
  private Integer page = 1;
  private Integer size = 100;
}
