package com.bone.architecture.fixture.context.mine.domain;

import com.bone.architecture.fixture.context.shared.domain.SharedValue;

/** 规则单测夹具：自身上下文 domain 类，仅依赖共享内核（合法）。 */
public class CleanAggregate {
  private final SharedValue shared;

  public CleanAggregate(SharedValue shared) {
    this.shared = shared;
  }

  public String summary() {
    return shared.value();
  }
}
