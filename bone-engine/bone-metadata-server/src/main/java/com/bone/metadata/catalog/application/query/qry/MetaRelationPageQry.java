package com.bone.metadata.catalog.application.query.qry;

import lombok.Data;

@Data
public class MetaRelationPageQry {
  private int pageNum = 1;
  private int pageSize = 10;
  private Long sourceEntityId;
  private Long targetEntityId;
  private String keyword;
}
