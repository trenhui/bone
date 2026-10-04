package com.bone.masterdata.application.query.qry;

import lombok.Data;

@Data
public class MasterDataEntityPageQuery {
  private int page = 1;
  private int size = 10;
  private String keyword;
  private String category;
  private String status;
}
