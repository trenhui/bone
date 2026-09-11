package com.bone.architecture.fixture.adapter.controller;

import com.bone.architecture.fixture.application.facade.OrderManager;
import com.bone.architecture.fixture.application.service.OrderAppService;

/** 规则单测夹具：Controller 直注服务实现与 Manager（违规）。 */
public class OrderController {
  private final OrderAppService appService;
  private final OrderManager manager;

  public OrderController(OrderAppService appService, OrderManager manager) {
    this.appService = appService;
    this.manager = manager;
  }

  public String summary() {
    return appService.name() + manager.name();
  }
}
