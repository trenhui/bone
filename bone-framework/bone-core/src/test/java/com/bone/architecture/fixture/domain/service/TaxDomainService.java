package com.bone.architecture.fixture.domain.service;

/** 规则单测夹具：领域服务（被 Controller 越层直注，adapterControllersMustNotDependOnDomainService 违规）。 */
public class TaxDomainService {

  public long rate(long amount) {
    return amount;
  }
}
