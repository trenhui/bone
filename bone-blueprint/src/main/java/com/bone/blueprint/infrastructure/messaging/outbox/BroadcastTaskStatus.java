package com.bone.blueprint.infrastructure.messaging.outbox;

/**
 * 渠道库存广播任务的投递状态。
 *
 * <p>与 {@code OutboxStatus} 同构但<strong>独立枚举</strong>：广播任务是「一次库存覆盖同步」，与「一条集成事件」的生命周期不同——
 * 它有重试上限、退避、死信与人工重试，把两套语义塞进同一个枚举会出现「事件没有死信、广播没有退避」这类读代码时的误导。
 */
public enum BroadcastTaskStatus {

  /** 待投递。 */
  PENDING,

  /** 已被中继抢占（进程崩溃会卡在这里，由 reconcileStuck 自愈回 PENDING）。 */
  PROCESSING,

  /** 投递成功。 */
  SENT,

  /** 死信：重试次数耗尽或遇到不可重试的失败（如渠道商品不存在），需人工介入。 */
  FAILED
}
