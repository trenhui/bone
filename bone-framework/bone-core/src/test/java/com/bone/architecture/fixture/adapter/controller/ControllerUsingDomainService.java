package com.bone.architecture.fixture.adapter.controller;

import com.bone.architecture.fixture.domain.service.TaxDomainService;

/** 规则单测夹具：Controller 直注领域服务（adapterControllersMustNotDependOnDomainService 违规）。 */
public class ControllerUsingDomainService {

  private final TaxDomainService taxDomainService;

  public ControllerUsingDomainService(TaxDomainService taxDomainService) {
    this.taxDomainService = taxDomainService;
  }

  public long calc(long amount) {
    return taxDomainService.rate(amount);
  }
}
