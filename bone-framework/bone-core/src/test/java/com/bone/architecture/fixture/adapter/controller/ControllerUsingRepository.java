package com.bone.architecture.fixture.adapter.controller;

import com.bone.architecture.fixture.domain.order.OrderAggregate;
import com.bone.architecture.fixture.domain.repository.OrderAggregateRepository;

/** 规则单测夹具：Controller 直注写侧仓储（adapterControllersMustNotDependOnDomainRepository 违规）。 */
public class ControllerUsingRepository {

  private final OrderAggregateRepository repository;

  public ControllerUsingRepository(OrderAggregateRepository repository) {
    this.repository = repository;
  }

  public OrderAggregate findByIdInTenant(Long id, Long tenantId) {
    return repository.findByIdInTenant(id, tenantId);
  }
}
