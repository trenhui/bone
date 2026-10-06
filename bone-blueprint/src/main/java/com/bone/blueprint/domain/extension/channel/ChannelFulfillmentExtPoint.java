package com.bone.blueprint.domain.extension.channel;

/**
 * 渠道履约扩展点（发货回传 / 物流轨迹查询，零框架依赖）。
 *
 * <p><b>为什么叫「履约」而不是「物流」</b>：本扩展点管的是「把货交给用户」这一段——回传运单号给渠道（发货）、 查渠道侧的配送进度（轨迹）。而「物流公司编码映射」（顺丰在淘宝是
 * {@code SF}、在京东是 {@code JD_007}）只是这两个能力内部的
 * 渠道差异细节。叫「物流」会让人以为它只负责承运商选择，实际上承运商选择早已由主数据侧决定，本接口只负责<strong>与渠道同步履约状态</strong>。
 *
 * <p><b>为什么这两个能力必须合在一起</b>：它们共用同一份「渠道订单 + 内部运单」上下文（{@link ChannelShipmentContext}），
 * 且都发生在发货这一时刻。拆成两个扩展点会让一个渠道实现写两个类、各自重复持有渠道会话与凭证。
 *
 * <p><b>为什么走扩展点而不是在发货服务里 {@code switch (channel)}</b>：各渠道的物流公司编码体系互不兼容 （淘宝用 {@code company_code}
 * 数值码、京东用自有物流商编码、抖音用字符串 code、拼多多用 ID）， 运单号回传的接口路径与签名方式也各不相同。若在下发服务里写分支，每接一个渠道都要改主流程，
 * 且无法做到「渠道实现独立发版」。
 */
public interface ChannelFulfillmentExtPoint {

  /**
   * 回传发货信息（承运商 + 运单号）到渠道。
   *
   * <p><b>不回传会被渠道判定虚假发货并罚款</b>，因此失败必须让上游感知（返回失败结果）， 不能静默忽略——本方法返回结果对象而非
   * boolean，就是为了让「渠道拒绝了」能带出原因码与文案。
   */
  ChannelShipmentResult pushShipment(ChannelShipmentContext request);

  /** 查询渠道侧物流轨迹（配送进度节点，按时间正序）。 */
  ChannelTraceResult queryTrace(ChannelShipmentContext request);
}
