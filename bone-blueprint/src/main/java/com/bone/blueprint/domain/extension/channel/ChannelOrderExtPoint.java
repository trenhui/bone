package com.bone.blueprint.domain.extension.channel;

/**
 * 渠道订单扩展点（业务契约，零框架依赖）。
 *
 * <p>多渠道订单接入的两个能力：
 *
 * <ul>
 *   <li><b>拉单归一化</b> {@link #pullOrder}：把渠道原始订单结构转换成内部统一的 {@link
 *       ChannelOrderDraft}。四个渠道的订单字段命名、金额口径、地址格式各不相同， 差异<strong>全部封死在扩展实现里</strong>，
 *       下单主流程只见统一草稿——新增第五个渠道时，下单服务一行都不用改。
 *   <li><b>状态回传</b> {@link #ackOrder}：订单发货 / 签收后回写渠道。不回传会被渠道判定虚假发货并罚款。
 * </ul>
 *
 * <p><b>路由</b>：由基础设施适配器按 {@code BizContext.dimension("channel", code)} 路由到对应渠道实现；
 * 未接入的渠道落到默认实现，由默认实现明确报错而<strong>不是</strong>静默返回空草稿—— 静默成功会让「渠道没接上」伪装成「渠道没订单」。
 */
public interface ChannelOrderExtPoint {

  /**
   * 拉取并归一化渠道订单。
   *
   * @param request 渠道订单上下文（含渠道原始明细）
   * @return 内部统一草稿
   */
  ChannelOrderDraft pullOrder(ChannelOrderContext request);

  /**
   * 订单状态回传渠道。
   *
   * @param request 渠道订单上下文
   * @return true=渠道已受理
   */
  boolean ackOrder(ChannelOrderContext request);
}
