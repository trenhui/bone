package com.bone.metadata.catalog.application.query.qry;

import lombok.Data;

@Data
public class MetaEntityPageQry {
  private int pageNum = 1;
  private int pageSize = 10;
  private String keyword;
  private Integer status;
}
