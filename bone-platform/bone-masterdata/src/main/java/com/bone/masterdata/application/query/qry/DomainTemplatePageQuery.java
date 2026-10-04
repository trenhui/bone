package com.bone.masterdata.application.query.qry;

import lombok.Data;

@Data
public class DomainTemplatePageQuery {
  private int page = 1;
  private int size = 10;
  private String status;
}
