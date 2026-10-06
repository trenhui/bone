package com.bone.blueprint.infrastructure.extension.channel;

import com.bone.blueprint.domain.extension.channel.ChannelShipmentContext;
import com.bone.blueprint.domain.extension.channel.ChannelShipmentResult;
import com.bone.blueprint.domain.extension.channel.ChannelTraceResult;
import com.bone.engine.extension.api.annotation.Extension;
import lombok.extern.slf4j.Slf4j;

/**
 * 渠道物流 · 默认兜底实现。
 *
 * <p><b>发货回传失败不影响本地发货状态</b>：本实现返回失败结果，由应用服务把发货单标记为 「渠道回传失败」但<strong>保留 SHIPPED
 * 状态</strong>——货已经发出，状态回退会制造未发货假象。 正确姿势是保留状态 + 记录原因 + 交补偿任务重试。
 */
@Slf4j
@Extension(name = "DEFAULT_CHANNEL_LOGISTICS_EXT", description = "渠道物流默认兜底实现（未接入渠道）")
public class DefaultChannelLogisticsExtension implements ExtensionChannelLogisticsExtPoint {

  @Override
  public ChannelShipmentResult pushShipment(ChannelShipmentContext request) {
    log.error("[DEFAULT] 渠道物流扩展未接入 | channel={}", request == null ? null : request.channelCode());
    return ChannelShipmentResult.fail(
        "CHANNEL_LOGISTICS_EXT_MISSING",
        "渠道未接入物流扩展实现: channel=" + (request == null ? null : request.channelCode()));
  }

  @Override
  public ChannelTraceResult queryTrace(ChannelShipmentContext request) {
    return ChannelTraceResult.fail(
        "CHANNEL_LOGISTICS_EXT_MISSING",
        "渠道未接入物流扩展实现: channel=" + (request == null ? null : request.channelCode()));
  }
}
