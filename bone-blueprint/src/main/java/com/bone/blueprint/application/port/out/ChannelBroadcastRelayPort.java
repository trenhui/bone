package com.bone.blueprint.application.port.out;

/**
 * 渠道库存广播中继端口——把「待广播任务」投递给渠道开放平台。
 *
 * <p><b>为何中继是端口而不是 application 内部方法</b>：中继需要「抢占（写库）→ 网络 IO（调渠道）→ 标记终态（写库）」三段， 其中网络 IO 必须在 DB
 * 事务外。端口化后，application 只表达「推进一轮」， 具体的抢占/退避/死信策略在 infrastructure 用 {@code TransactionTemplate}
 * 显式控制事务边界（{@code OrderOutboxRelayPort} 同构）。
 */
public interface ChannelBroadcastRelayPort {

  /**
   * 推进一轮：抢占待投递任务 → 逐条调渠道 → 标记成功/退避重试/死信。
   *
   * @return 本轮投递成功条数
   */
  int relayPending();
}
