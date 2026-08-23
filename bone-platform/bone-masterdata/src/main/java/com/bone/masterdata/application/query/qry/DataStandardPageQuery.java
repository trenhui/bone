package com.bone.masterdata.application.query.qry;

import lombok.Data;

@Data
public class DataStandardPageQuery {
  private String entityCode;
  private String keyword;
  private int pageNum = 1;
  private int pageSize = 10;
}
