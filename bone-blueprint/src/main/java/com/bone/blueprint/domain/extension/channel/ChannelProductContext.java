package com.bone.blueprint.domain.extension.channel;

import java.math.BigDecimal;

/**
 * 渠道商品上下文（上架 / 下架 / 库存同步共用入参）。
 *
 * @param tenantId 租户ID
 * @param channelCode 渠道码
 * @param productId 内部商品ID
 * @param productName 商品名称（提交给渠道的标题）
 * @param listingPrice 挂牌价
 * @param stock 待同步库存（库存同步场景用；可空——下架场景不携带库存）
 * @param channelProductId 渠道侧商品ID（下架 / 改价 / 同步库存场景必填，上架场景为空）
 */
public record ChannelProductContext(
    Long tenantId,
    String channelCode,
    Long productId,
    String productName,
    BigDecimal listingPrice,
    Integer stock,
    String channelProductId) {

  /** 构造上架上下文（尚无渠道商品ID）。 */
  public static ChannelProductContext forListing(
      Long tenantId, String channelCode, Long productId, String productName, BigDecimal price) {
    return new ChannelProductContext(tenantId, channelCode, productId, productName, price, 0, null);
  }

  /** 构造下架 / 同步上下文（已有渠道商品ID）。 */
  public static ChannelProductContext forExisting(
      Long tenantId,
      String channelCode,
      Long productId,
      String productName,
      String channelProductId,
      Integer stock) {
    return new ChannelProductContext(
        tenantId, channelCode, productId, productName, null, stock, channelProductId);
  }
}
