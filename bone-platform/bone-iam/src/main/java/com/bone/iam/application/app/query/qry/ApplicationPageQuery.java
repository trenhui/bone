package com.bone.iam.application.app.query.qry;

import lombok.Data;

@Data
public class ApplicationPageQuery {
  private Integer page = 1;
  private Integer size = 10;
  private String keyword;
  private Integer status;
}
