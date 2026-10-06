package com.bone.blueprint.domain.extension.channel;

/**
 * 渠道商品扩展点（上架 / 下架 / 库存同步）。
 *
 * <p><b>为何三个能力合在一个扩展点</b>：三者共享同一份「渠道商品会话」（appKey、类目映射、 资质），拆成三个扩展点会让一个渠道实现要写三个类、各自重复持有凭证与地址映射。
 * 合在一起则「接一个渠道 = 写一个类」，接入成本最小。
 *
 * <p><b>库存同步必须存在的原因</b>：多渠道共享同一份实物库存。淘宝卖掉一件后若不同步给抖音， 抖音侧仍按旧库存售卖 →
 * 超卖。因此库存落库后必须广播到<strong>所有已上架渠道</strong> （见 {@code
 * ChannelProductApplicationService#syncInventoryToChannels}）。
 */
public interface ChannelProductExtPoint {

  /** 上架商品到渠道；成功返回渠道侧商品ID。 */
  ChannelListingResult listProduct(ChannelProductContext request);

  /** 从渠道下架商品。 */
  ChannelListingResult delistProduct(ChannelProductContext request);

  /** 同步库存到渠道。 */
  ChannelListingResult syncInventory(ChannelProductContext request);
}
