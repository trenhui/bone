package com.bone.blueprint.domain.extension.channel;

/**
 * 渠道物流扩展点（发货回传 / 轨迹查询）。
 *
 * <p><b>为何物流也要走扩展点</b>：各渠道的物流公司编码体系互不兼容（淘宝用 {@code company_code} 数值码、京东用自有物流商编码、抖音用字符串 code、拼多多用
 * ID），运单号回传的接口路径与签名方式 也各不相同。若在下发服务里写 {@code switch (channel)}，每接一个渠道都要改主流程， 且无法做到「渠道实现独立发版」。
 */
public interface ChannelLogisticsExtPoint {

  /** 回传发货信息（运单号）到渠道。 */
  ChannelShipmentResult pushShipment(ChannelShipmentContext request);

  /** 查询渠道侧物流轨迹。 */
  ChannelTraceResult queryTrace(ChannelShipmentContext request);
}
