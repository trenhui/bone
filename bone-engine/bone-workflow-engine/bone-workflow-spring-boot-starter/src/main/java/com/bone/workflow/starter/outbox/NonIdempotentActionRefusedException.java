package com.bone.workflow.starter.outbox;

/**
 * 中继幂等硬约束命中（P0-2）：目标动作未声明幂等，at-least-once 重投会双推进业务状态，故拒绝执行。
 *
 * <p>中继捕获本异常后<b>不</b>走退避重试（重试正是危险源），而是把意图落终态 FAILED 交由人工介入—— 这与「动作已是幂等」时的正常重试路径（{@link
 * WorkflowStatusOutboxRepository#markFailed}）明确区分。
 */
public class NonIdempotentActionRefusedException extends RuntimeException {

  public NonIdempotentActionRefusedException(String message) {
    super(message);
  }
}
