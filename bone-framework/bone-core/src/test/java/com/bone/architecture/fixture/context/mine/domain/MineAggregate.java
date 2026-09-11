package com.bone.architecture.fixture.context.mine.domain;

import com.bone.architecture.fixture.context.other.domain.OtherAggregate;

/** 规则单测夹具：自身上下文 domain 类，越界依赖其它上下文 domain（违规）。 */
public class MineAggregate {
  private final OtherAggregate other;

  public MineAggregate(OtherAggregate other) {
    this.other = other;
  }

  public String summary() {
    return other.name();
  }
}
