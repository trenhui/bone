package com.bone.blueprint.domain.extension.channel;

import java.math.BigDecimal;
import java.util.List;

/**
 * 渠道订单上下文（扩展点入参，领域内纯类型）。
 *
 * <p><b>为何不直接把 {@code BizContext} 传进来</b>：{@code BizContext} 是扩展引擎 SDK 的类型， domain 层若引用它就违反了「domain
 * 零框架依赖」的架构硬约束（ArchUnit {@code domainMustNotDependOnOuterLayers}）。因此扩展点方法只收领域类型，由基础设施适配器在调用前
 * 把上下文装配进 {@code BizContext} 并压入 {@code ExtensionContextManager}——路由对业务代码透明。
 *
 * @param tenantId 租户ID（扩展实现做租户级差异时用）
 * @param channelCode 渠道码（TAOBAO / JD / DOUYIN / PDD）
 * @param channelOrderNo 渠道侧原始订单号
 * @param buyerNick 渠道买家昵称（不同渠道字段名不同，此处为归一化后的值）
 * @param buyerId 渠道买家账号ID（淘宝 {@code buyer_user_id} / 京东 {@code buyerdno} / 抖音 {@code
 *     buyer_second_id} / 拼多多 {@code user_id}）——「渠道买家 ↔ 内部客户」映射的键，昵称会改、ID 才是稳定身份
 * @param payAmount 渠道回传的实付金额（含运费）
 * @param receiverName 收货人
 * @param receiverPhone 收货电话
 * @param receiverAddress 收货地址
 * @param lines 渠道订单明细（已按渠道原始结构取出，未做内部商品映射）
 */
public record ChannelOrderContext(
    Long tenantId,
    String channelCode,
    String channelOrderNo,
    String buyerNick,
    String buyerId,
    BigDecimal payAmount,
    String receiverName,
    String receiverPhone,
    String receiverAddress,
    List<ChannelOrderLine> lines) {

  public ChannelOrderContext {
    if (lines == null) {
      lines = List.of();
    }
  }
}
