package com.bone.metadata.catalog.application.query.qry;

import lombok.Data;

@Data
public class MetaFieldPageQuery {
  private int page = 1;
  private int size = 10;
  private Long entityId;
  private String keyword;
}
