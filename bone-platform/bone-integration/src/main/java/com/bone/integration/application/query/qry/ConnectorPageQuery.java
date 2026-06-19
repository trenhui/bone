package com.bone.integration.application.query.qry;

public record ConnectorPageQuery(
    Integer pageNum, Integer pageSize, String keyword, String type, String status) {
  public ConnectorPageQuery {
    if (pageNum == null || pageNum <= 0) pageNum = 1;
    if (pageSize == null || pageSize <= 0) pageSize = 10;
  }
}
