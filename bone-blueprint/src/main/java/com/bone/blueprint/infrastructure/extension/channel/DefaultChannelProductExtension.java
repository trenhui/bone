package com.bone.blueprint.infrastructure.extension.channel;

import com.bone.blueprint.domain.extension.channel.ChannelListingResult;
import com.bone.blueprint.domain.extension.channel.ChannelProductContext;
import com.bone.engine.extension.api.annotation.Extension;
import lombok.extern.slf4j.Slf4j;

/**
 * 渠道商品 · 默认兜底实现（未接入渠道统一返回失败结果，而非抛异常）。
 *
 * <p><b>与订单默认实现的差异</b>：商品上架是「提交给外部渠道」的动作，渠道未接入时 应当产生一条 <strong>FAILED
 * 记录</strong>（运营能在列表里看到并重试），而不是让整个请求 500。 因此这里返回 {@link ChannelListingResult#fail}，由应用服务把实体置为
 * FAILED 态。 订单拉单则相反——拉不到就是流程无法继续，必须显式失败。
 */
@Slf4j
@Extension(name = "DEFAULT_CHANNEL_PRODUCT_EXT", description = "渠道商品默认兜底实现（未接入渠道）")
public class DefaultChannelProductExtension implements ExtensionChannelProductExtPoint {

  @Override
  public ChannelListingResult listProduct(ChannelProductContext request) {
    log.error("[DEFAULT] 渠道商品上架扩展未接入 | channel={}", request == null ? null : request.channelCode());
    return ChannelListingResult.fail(
        "CHANNEL_PRODUCT_EXT_MISSING",
        "渠道未接入商品扩展实现: channel=" + (request == null ? null : request.channelCode()));
  }

  @Override
  public ChannelListingResult delistProduct(ChannelProductContext request) {
    return ChannelListingResult.fail(
        "CHANNEL_PRODUCT_EXT_MISSING",
        "渠道未接入商品扩展实现: channel=" + (request == null ? null : request.channelCode()));
  }

  @Override
  public ChannelListingResult syncInventory(ChannelProductContext request) {
    return ChannelListingResult.fail(
        "CHANNEL_PRODUCT_EXT_MISSING",
        "渠道未接入商品扩展实现: channel=" + (request == null ? null : request.channelCode()));
  }
}
