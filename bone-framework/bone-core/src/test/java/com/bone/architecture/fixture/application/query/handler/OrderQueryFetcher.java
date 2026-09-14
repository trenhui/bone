package com.bone.architecture.fixture.application.query.handler;

/** 规则单测夹具：命名不合规（缺 QueryHandler 后缀）。 */
public class OrderQueryFetcher {

  public String handle(String query) {
    return "row";
  }
}
