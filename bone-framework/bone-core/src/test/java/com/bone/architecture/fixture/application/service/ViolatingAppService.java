package com.bone.architecture.fixture.application.service;

import com.bone.architecture.fixture.domain.order.OrderAggregate;

/** 规则单测夹具：违规的 application.service——new 领域对象 + 调聚合 setter（E-5.3.1 双违例）。 */
public class ViolatingAppService {

  public OrderAggregate build() {
    OrderAggregate aggregate = new OrderAggregate();
    aggregate.setId(1L);
    return aggregate;
  }
}
