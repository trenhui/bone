package com.bone.claim.application.flow;

/** 流转结果：{@code advanced=false} 一律是 fail-closed 停止，不是「条件不成立」的软失败。 */
public record FlowOutcome(boolean advanced, String stage, String nextStage, String reasonCode) {

  public static FlowOutcome advanced(Stage stage) {
    return new FlowOutcome(true, stage.name(), stage.next(), null);
  }

  public static FlowOutcome halted(Stage stage, String reasonCode) {
    return new FlowOutcome(false, stage.name(), null, reasonCode);
  }
}
