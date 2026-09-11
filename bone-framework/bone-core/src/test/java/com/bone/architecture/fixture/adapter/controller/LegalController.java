package com.bone.architecture.fixture.adapter.controller;

import com.bone.architecture.fixture.application.facade.OrderFacade;
import com.bone.architecture.fixture.application.orchestration.OrderOrchestration;

/** 规则单测夹具：Controller 仅依赖门面 / 编排层（合法）。 */
public class LegalController {
  private final OrderFacade facade;
  private final OrderOrchestration orchestration;

  public LegalController(OrderFacade facade, OrderOrchestration orchestration) {
    this.facade = facade;
    this.orchestration = orchestration;
  }

  public String summary() {
    return facade.summary() + orchestration.name();
  }
}
