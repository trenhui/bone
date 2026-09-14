package com.bone.architecture.fixture.infrastructure.query;

import com.bone.core.annotation.ReadSideOnly;

/** 规则单测夹具：读侧 DSL 类型（被违规方依赖；本身放 infrastructure/query 属落位）。 */
@ReadSideOnly
public class ReadDslGateway {

  public String rows() {
    return "rows";
  }
}
