package com.bone.integration.application.query.qry;

public record FlowPageQuery(Integer pageNum, Integer pageSize, String keyword, String status) {
  public FlowPageQuery {
    if (pageNum == null || pageNum <= 0) pageNum = 1;
    if (pageSize == null || pageSize <= 0) pageSize = 10;
  }
}
