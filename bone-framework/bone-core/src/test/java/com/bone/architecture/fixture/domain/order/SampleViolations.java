package com.bone.architecture.fixture.domain.order;

import com.bone.architecture.fixture.infrastructure.OuterLayerGateway;

/** 规则单测夹具：domain 类依赖外层网关（domainMustNotDependOnOuterLayers 违规）。 */
public class SampleViolations {

  private final OuterLayerGateway gateway;

  public SampleViolations(OuterLayerGateway gateway) {
    this.gateway = gateway;
  }

  public String call() {
    return gateway.ping();
  }
}
