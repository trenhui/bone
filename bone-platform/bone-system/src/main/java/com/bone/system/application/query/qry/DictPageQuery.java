package com.bone.system.application.query.qry;

import lombok.Data;

@Data
public class DictPageQuery {
  private String type;
  private String keyword;
  private int pageNum = 1;
  private int pageSize = 10;
}
