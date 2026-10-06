package com.bone.blueprint.domain.model.channel.event;

import com.bone.core.domain.DomainEvent;
import java.time.Instant;

/**
 * 渠道完成一次扩展点路由调用（拉单 / 上架 / 发货 / 同步）。
 *
 * <p><b>为何要以事件形式外发，而不是在业务事务里顺手 {@code channelRepository.update(channel)}</b>： 渠道（{@code
 * Channel}）与订单（{@code Order}）、渠道商品（{@code ChannelProduct}）是<strong>三个不同的聚合</strong>。
 * 在一个写事务里同时改两个聚合（R9 一事务一聚合）会让「订单落库成功但渠道同步标记没写」和反之都无法区分， 也会把订单事务的锁范围扩大到渠道行。可观测标记（{@code extImplCode}
 * / {@code lastSyncAt}） 属于<strong>另一个聚合的投影</strong>，正确做法是提交后由订阅方在独立事务里写。
 *
 * <p><b>语义</b>：仅表达「这次调用命中了渠道 X 的某个扩展」，不代表业务成功。 订阅方据此刷新渠道的可观测字段；订阅失败只损失可观测性，不影响订单/上架结果。
 *
 * @param tenantId 租户ID
 * @param channelCode 渠道码（{@code TAOBAO/JD/DOUYIN/PDD}）
 * @param useCase 业务场景（{@code PULL_ORDER/LIST_PRODUCT/...}），与扩展点路由的场景维度一致
 * @param occurredAt 发生时间
 */
public record ChannelRoutedEvent(
    Long tenantId, String channelCode, String useCase, Instant occurredAt) implements DomainEvent {}
