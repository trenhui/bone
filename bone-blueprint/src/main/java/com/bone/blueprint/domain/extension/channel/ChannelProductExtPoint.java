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
 *
 * <p><b>方法名为何用 publish 而非 list（2026-10-06 修正）</b>：原名 {@code listProduct} 有真实歧义—— 它与同域的 {@code
 * page()}（分页查列表）并列时，{@code list} 前缀在 Java 生态里天然读作「查询列表」， 而本方法实际是「把商品发布到渠道」这一写操作。更糟的是 REST 层同时存在
 * {@code @GetMapping}（分页列表，summary「渠道商品分页列表」）与本方法（{@code @PostMapping}，summary「商品上架到渠道」）， 同一个
 * {@code list} 字面表达了两件毫不相干的事，读代码的人必须逐个点开签名才能分辨。 改为 {@code publishProduct} 后：写操作语义明确，与 {@code
 * delistProduct}（下架）成对，且与 {@code page()} 不再撞名。
 */
public interface ChannelProductExtPoint {

  /**
   * 上架商品到渠道（写操作）。
   *
   * @return 上架结果，成功时携带渠道侧商品ID
   */
  ChannelListingResult publishProduct(ChannelProductContext request);

  /** 从渠道下架商品（写操作）。与 {@link #publishProduct} 成对。 */
  ChannelListingResult delistProduct(ChannelProductContext request);

  /** 同步库存到渠道（写操作）。 */
  ChannelListingResult syncInventory(ChannelProductContext request);
}
