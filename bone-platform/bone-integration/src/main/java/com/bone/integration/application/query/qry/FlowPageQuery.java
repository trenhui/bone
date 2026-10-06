package com.bone.integration.application.query.qry;

public record FlowPageQuery(Integer page, Integer size, String keyword, String status) {
  public FlowPageQuery {
    if (page == null || page <= 0) page = 1;
    if (size == null || size <= 0) size = 10;
  }
}
