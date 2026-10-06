package com.bone.blueprint.domain.model.channelbuyer.event;

import com.bone.core.domain.DomainEvent;
import java.time.Instant;

/**
 * 渠道买家被观测到的事件（拉单时命中某个渠道买家）。
 *
 * <p><b>为什么用事件而不是在拉单事务里直接写映射</b>：拉单主事务只允许碰 {@code Order} 一个聚合（R9 一事务一聚合）， 而「渠道买家映射」是另一个聚合。 在同一事务里
 * insert 映射会被架构门禁拦下， 硬塞则会让「订单没建成、映射先建成」留下悬挂数据。
 * 因此拉单事务内<strong>只读</strong>映射（读不违反聚合边界），未命中或需要累计笔数时发布本事件， 由提交后的独立事务完成写入 （{@code
 * ChannelBuyerObservedEventHandler}）。
 *
 * <p><b>一致性取舍</b>：这是<strong>最终一致</strong>——订单先落库，映射随后补齐。对本业务可接受：映射缺失只影响「客户维度归属」， 不影响订单成立与库存；且事件带
 * {@code channelBuyerId}，重复投递由唯一键兜底（同一买家两次并发拉单最多留下一条映射）。
 *
 * @param tenantId 租户ID（{@code Long}，AFTER_COMMIT 线程无 HTTP 上下文，必须由事件携带）
 * @param channelCode 渠道码
 * @param channelBuyerId 渠道买家账号ID（映射键）
 * @param channelBuyerNick 渠道买家昵称快照（可为 {@code null}）
 * @param orderId 触发本次观测的内部订单ID（仅日志追踪用）
 * @param occurredAt 事件发生时间
 */
public record ChannelBuyerObservedEvent(
    Long tenantId,
    String channelCode,
    String channelBuyerId,
    String channelBuyerNick,
    Long orderId,
    Instant occurredAt)
    implements DomainEvent {

  public static ChannelBuyerObservedEvent of(
      Long tenantId,
      String channelCode,
      String channelBuyerId,
      String channelBuyerNick,
      Long orderId) {
    return new ChannelBuyerObservedEvent(
        tenantId, channelCode, channelBuyerId, channelBuyerNick, orderId, Instant.now());
  }
}
