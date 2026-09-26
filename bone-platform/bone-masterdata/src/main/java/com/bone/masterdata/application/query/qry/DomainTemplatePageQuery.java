package com.bone.masterdata.application.query.qry;

import lombok.Data;

@Data
public class DomainTemplatePageQuery {
  private int pageNum = 1;
  private int pageSize = 10;
  private String status;
}
