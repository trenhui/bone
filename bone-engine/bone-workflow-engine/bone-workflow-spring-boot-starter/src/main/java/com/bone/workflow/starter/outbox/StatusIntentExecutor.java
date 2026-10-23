package com.bone.workflow.starter.outbox;

/**
 * 状态投影意图执行器（可替换）。
 *
 * <p>中继只负责「何时重试、按什么顺序、记什么状态」，把「怎么把这个意图落到业务单据上」交给执行器—— 宿主可替换为其它落地方式（如消息总线），不必改中继。
 */
public interface StatusIntentExecutor {

  /**
   * 执行一条意图（把业务单据推进到对应状态）。
   *
   * @param intent 意图
   * @throws RuntimeException 执行失败——中继据此排期重试
   */
  void execute(StatusIntent intent);
}
