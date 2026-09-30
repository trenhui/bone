package com.bone.blueprint.application.command;

import java.math.BigDecimal;
import java.util.List;

/**
 * 创建订单命令（不可变 record，含订单项列表）。
 *
 * <p>真实交易场景的订单不止「客户 + 明细」：来源渠道决定运营归因与后续营销触达； 运费与优惠是金额三口径（总额 = 明细小计之和 + 运费 −
 * 优惠）的组成部分——缺这两项就无法解释「用户看到的应付金额从哪来」。
 *
 * @param customerId 客户 ID
 * @param items 订单项
 * @param channelSource 来源渠道（APP / H5 / 小程序 / POS），可空
 * @param freightAmount 运费，可空视为 0
 * @param discountAmount 优惠总额，可空视为 0
 */
public record CreateOrderCommand(
    Long customerId,
    List<OrderItemDto> items,
    String channelSource,
    BigDecimal freightAmount,
    BigDecimal discountAmount) {

  /** 兼容「无渠道 / 无金额附加项」的下单场景。 */
  public CreateOrderCommand(Long customerId, List<OrderItemDto> items) {
    this(customerId, items, null, null, null);
  }

  /** 订单项（命令入参，不可变）。 */
  public record OrderItemDto(
      Long productId, String productName, Integer quantity, BigDecimal unitPrice) {}
}
