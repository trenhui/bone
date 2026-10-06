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
   * 把发货信息回传渠道（订单已发货后调用）。
   *
   * <p><b>为何入参是 {@link ChannelShipmentContext} 而不是 {@link ChannelOrderContext}</b>：回传必然要带承运商与运单号， 而
   * {@code ChannelOrderContext} 里没有这两个字段。早期实现只传订单号、承运商与运单号在实现里写死 （曾硬编码 {@code company_name="SF" /
   * tracking_no="SF0000000000"}），那意味着<strong>无论真实发什么货，
   * 回传给渠道的都是同一串假单号</strong>——渠道侧据此判定虚假发货，本地却显示"回传成功"。 契约层强制携带真实物流信息，从签名上就杜绝伪造。
   *
   * <p>与 {@link ChannelFulfillmentExtPoint#pushShipment} 的区别：后者是履约链路的正式回传（带失败落库与重试），
   * 本方法用于人工/补偿触发。二者最终都应汇到同一份渠道回传能力上，不要各自实现一套 HTTP 调用。
   *
   * @param request 渠道发货上下文（含承运商与运单号）
   * @return true=渠道已受理
   */
  boolean ackOrder(ChannelShipmentContext request);
}
