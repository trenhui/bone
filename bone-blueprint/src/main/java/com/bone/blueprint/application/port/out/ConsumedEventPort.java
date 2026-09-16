package com.bone.blueprint.application.port.out;

/**
 * 消费端幂等端口：以 {@code eventId} 为键<strong>落库</strong>去重（《Bone-消息与事件规范》§5）。
 *
 * <p><b>为什么必须落库</b>：Outbox 是<strong>至少一次</strong>投递——MQ 已收但标记 SENT 前宕机、或中继重试都会重投。 用进程内存 {@code Set}
 * 去重只能挡住「同一进程存活期内」的重复，重启后照旧重复消费，等于把「丢事件」换成了「重复扣款」。
 *
 * <p><b>为什么放在 {@code application/port/out}</b>：规范把「身份生成、幂等、通知」等技术能力统一划归 {@code
 * application/port/out}（E-4.3），由 infrastructure 以去重表实现。入站适配器（MQ 监听）依赖本端口， 不直接依赖去重表仓储。
 */
public interface ConsumedEventPort {

  /**
   * 抢占式登记：首次消费返回 {@code true}，重复投递返回 {@code false}。
   *
   * <p>实现必须基于唯一键原子插入（而非「先查后写」）——先查后写在并发重复投递下会双写通过。
   *
   * @param consumerGroup 消费组：同一 eventId 允许被不同消费组各消费一次
   * @param topic 来源 Topic，便于排障与按主题统计
   * @param eventId 信封 {@code eventId}（幂等键）
   * @param tenantId 事件所属租户（记录维度，便于多租户排障）
   * @return {@code true} 可继续处理；{@code false} 已处理过，应直接跳过
   */
  boolean tryClaim(String consumerGroup, String topic, String eventId, long tenantId);
}
