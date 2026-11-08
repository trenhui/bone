package com.bone.claim.application.flow;

/** 阶段触发器：事件名用于匹配，条件用于决定是否推进。 */
public record Trigger(String event, Condition condition) {

  public Trigger {
    if (event == null || event.isBlank()) {
      throw new IllegalArgumentException("触发事件不能为空");
    }
    if (condition == null) {
      throw new IllegalArgumentException("触发条件不能为空");
    }
  }
}
