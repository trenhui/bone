package com.bone.integration.application.query.qry;

public record ConnectorPageQuery(
    Integer page, Integer size, String keyword, String type, String status) {
  public ConnectorPageQuery {
    if (page == null || page <= 0) page = 1;
    if (size == null || size <= 0) size = 10;
  }
}
