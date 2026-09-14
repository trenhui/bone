package com.bone.architecture.fixture.domain.order;

import com.bone.architecture.fixture.infrastructure.query.ReadDslGateway;

/** 规则单测夹具：domain 类依赖 @ReadSideOnly DSL（domainMustNotUseQueryBuilder 违规）。 */
public class DomainUsingReadDsl {

  public String run() {
    return new ReadDslGateway().rows();
  }
}
