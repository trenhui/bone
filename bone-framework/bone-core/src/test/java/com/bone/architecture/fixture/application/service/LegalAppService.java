package com.bone.architecture.fixture.application.service;

import com.bone.architecture.fixture.domain.order.OrderAggregate;
import com.bone.architecture.fixture.domain.repository.OrderAggregateRepository;

/** 规则单测夹具：合规的应用服务——从仓储获取聚合（不 new 领域对象、不调 setter）。 */
public class LegalAppService {

  private final OrderAggregateRepository repository;

  public LegalAppService(OrderAggregateRepository repository) {
    this.repository = repository;
  }

  public String describe(Long id) {
    OrderAggregate aggregate = repository.findByIdInTenant(id, 1L);
    return aggregate != null ? "order-" + aggregate.getId() : "empty";
  }
}
