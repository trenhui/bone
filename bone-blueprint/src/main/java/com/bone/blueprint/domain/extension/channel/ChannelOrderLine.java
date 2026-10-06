package com.bone.blueprint.domain.extension.channel;

import com.bone.core.exception.DomainException;
import java.math.BigDecimal;

/**
 * 渠道订单明细行。
 *
 * @param outerSkuId 渠道侧 SKU 编码（淘宝 num_iid / 京东 skuId / 抖音 sku_id / 拼多多 sku_id）
 * @param title 渠道侧商品标题（与内部商品名可能不同，运营会在渠道后台改标题）
 * @param quantity 购买数量
 * @param unitPrice 渠道侧成交单价
 */
public record ChannelOrderLine(
    String outerSkuId, String title, int quantity, BigDecimal unitPrice) {

  public ChannelOrderLine {
    if (quantity <= 0) {
      throw new DomainException("渠道订单明细数量必须为正: " + quantity);
    }
  }
}
