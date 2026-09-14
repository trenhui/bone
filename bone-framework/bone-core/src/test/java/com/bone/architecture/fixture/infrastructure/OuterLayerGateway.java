package com.bone.architecture.fixture.infrastructure;

/** 规则单测夹具：外层网关（被 domain 类依赖，触发 domainMustNotDependOnOuterLayers 违例报告）。 */
public class OuterLayerGateway {

  public String ping() {
    return "pong";
  }
}
