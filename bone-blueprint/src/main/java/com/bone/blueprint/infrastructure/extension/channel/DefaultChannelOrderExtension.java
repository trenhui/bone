package com.bone.blueprint.infrastructure.extension.channel;

import com.bone.blueprint.domain.extension.channel.ChannelOrderContext;
import com.bone.blueprint.domain.extension.channel.ChannelOrderDraft;
import com.bone.blueprint.domain.extension.channel.ChannelShipmentContext;
import com.bone.engine.extension.api.annotation.Extension;
import lombok.extern.slf4j.Slf4j;

/**
 * 渠道订单 · 默认兜底实现。
 *
 * <p><b>为何必须存在一个「只会失败」的默认实现</b>：四级路由的兜底（L4）如果不存在， 未接入渠道的调用会抛 {@code
 * RouterException(NO_MATCH)}——那是一个 500 级系统异常，
 * 会把「渠道没接上」这个<strong>配置问题</strong>报成<strong>系统故障</strong>，污染 5xx 告警。 有了本实现，未接入渠道会得到一个明确语义的失败（抛
 * {@link IllegalArgumentException} 携带渠道码）， 且排障时一眼可定位到「渠道码 X 未注册扩展实现」。
 *
 * <p><b>为何本类不带任何路由维度</b>：扩展引擎判定「默认实现」的条件是 {@code tenant/bizCode/useCase/scenario/env} 全为 {@code *}
 * 且无 {@code condition} （见 {@code ExtensionRegister#isDefaultImplementation}）。一旦给本类加上任一维度， 它就会在 L3
 * 模糊匹配里参与竞争，可能抢掉真实渠道实现的流量——那是极难排查的静默错误。
 */
@Slf4j
@Extension(name = "DEFAULT_CHANNEL_ORDER_EXT", description = "渠道订单默认兜底实现（未接入渠道）")
public class DefaultChannelOrderExtension implements ExtensionChannelOrderExtPoint {

  @Override
  public ChannelOrderDraft pullOrder(ChannelOrderContext request) {
    log.error(
        "[DEFAULT] 渠道订单扩展未接入 | channel={} | orderNo={}",
        request == null ? null : request.channelCode(),
        request == null ? null : request.channelOrderNo());
    throw new IllegalArgumentException(
        "渠道未接入订单扩展实现，请先登记渠道扩展实现: channel=" + (request == null ? null : request.channelCode()));
  }

  @Override
  public boolean ackOrder(ChannelShipmentContext request) {
    log.error("[DEFAULT] 渠道订单回传扩展未接入 | channel={}", request == null ? null : request.channelCode());
    return false;
  }
}
