package com.bone.architecture.fixture.adapter.controller;

import com.bone.architecture.fixture.domain.order.SpoofedAggregate;

/** 规则单测夹具：Controller 调聚合 setId（outerLayersMustNotMutateAggregateIdentity 违规）。 */
public class SpoofingController {

  public void spoof(SpoofedAggregate aggregate) {
    aggregate.setId(42L);
  }
}
