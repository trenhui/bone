package com.bone.metadata.catalog.application.query.qry;

import lombok.Data;

@Data
public class MetaFieldPageQuery {
  private int pageNum = 1;
  private int pageSize = 10;
  private Long entityId;
  private String keyword;
}
