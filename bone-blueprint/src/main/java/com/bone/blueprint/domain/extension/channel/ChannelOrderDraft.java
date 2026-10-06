package com.bone.blueprint.domain.extension.channel;

import java.math.BigDecimal;
import java.util.List;

/**
 * 渠道订单归一化草稿 —— 渠道原始订单经扩展实现转换后的<strong>内部统一形态</strong>。
 *
 * <p><b>为何要有这一层</b>：四个渠道的订单结构差异极大（淘宝的 {@code tid/oid}、京东的 {@code orderId/skuId}、抖音的 {@code
 * p_id}、拼多多的 {@code order_sn}），若让下单服务直接消费渠道原始 JSON，则每接一个渠道都要改下单主流程。扩展实现把差异全部吃掉，输出统一草稿， 下单服务只见
 * {@code ChannelOrderDraft}——这是扩展点机制在多渠道场景的核心价值。
 *
 * @param channelCode 渠道码
 * @param channelOrderNo 渠道原始订单号（落 t_order.channel_order_no，用于对账与幂等）
 * @param channelSource 下单终端映射值（WEB/APP/MINI），由渠道类型推导
 * @param lines 归一化明细（已映射为内部商品ID）
 * @param freightAmount 运费
 * @param discountAmount 渠道优惠（平台补贴、店铺券）
 * @param receiverName 收货人
 * @param receiverPhone 收货电话
 * @param receiverAddress 收货地址
 */
public record ChannelOrderDraft(
    String channelCode,
    String channelOrderNo,
    String channelSource,
    List<ChannelDraftLine> lines,
    BigDecimal freightAmount,
    BigDecimal discountAmount,
    String receiverName,
    String receiverPhone,
    String receiverAddress) {

  public ChannelOrderDraft {
    if (lines == null) {
      lines = List.of();
    }
  }

  /** 归一化明细（已映射为内部商品 ID）。 */
  public record ChannelDraftLine(
      Long productId, String productName, int quantity, BigDecimal unitPrice) {}

  public BigDecimal goodsAmount() {
    BigDecimal sum = BigDecimal.ZERO;
    for (ChannelDraftLine line : lines) {
      if (line.unitPrice() != null) {
        sum = sum.add(line.unitPrice().multiply(BigDecimal.valueOf(line.quantity())));
      }
    }
    return sum;
  }

  /** 订单总额 = 货款 + 运费 − 优惠（与 {@code Order#recalculateTotal} 口径一致，下限 0）。 */
  public BigDecimal totalAmount() {
    BigDecimal total =
        goodsAmount()
            .add(freightAmount == null ? BigDecimal.ZERO : freightAmount)
            .subtract(discountAmount == null ? BigDecimal.ZERO : discountAmount);
    return total.max(BigDecimal.ZERO);
  }
}
