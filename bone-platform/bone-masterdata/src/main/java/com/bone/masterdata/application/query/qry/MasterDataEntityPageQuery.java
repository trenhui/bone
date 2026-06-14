package com.bone.masterdata.application.query.qry;

import lombok.Data;

@Data
public class MasterDataEntityPageQuery {
  private int pageNum = 1;
  private int pageSize = 10;
  private String keyword;
  private String category;
  private String status;
}
