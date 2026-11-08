package com.bone.claim.application.flow;

/** 流程阶段：做什么（节点名）、什么条件落到这个阶段（Trigger）、推进到哪（next）。 */
public record Stage(String name, Trigger trigger, String next) {

  public Stage {
    if (name == null || name.isBlank()) {
      throw new IllegalArgumentException("阶段名不能为空");
    }
    if (trigger == null) {
      throw new IllegalArgumentException("阶段触发器不能为空");
    }
  }
}
