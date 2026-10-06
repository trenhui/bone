package com.bone.blueprint.infrastructure.extension.channel;

import com.bone.blueprint.domain.extension.channel.ChannelListingResult;
import com.bone.blueprint.domain.extension.channel.ChannelProductContext;
import com.bone.blueprint.infrastructure.channel.openapi.ChannelApiRequest;
import com.bone.blueprint.infrastructure.channel.openapi.ChannelApiResult;
import com.bone.blueprint.infrastructure.channel.openapi.ChannelJson;
import com.bone.blueprint.infrastructure.channel.openapi.ChannelOpenApiClient;
import com.bone.engine.extension.api.annotation.Extension;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 抖音电商渠道 · 商品扩展实现（对接真实抖音电商开放平台 openapi-fxg.jinritemai.com）。
 *
 * <p><b>接口映射</b>：上架 {@code product.addV2}、下架 {@code product.offline}、库存 {@code
 * product.updateStock}。 抖音是四平台里唯一用 <b>HMAC-SHA256</b> 签名、且令牌走 Header（{@code
 * Access-Token}，不参与待签串）的，这些差异全部收在 {@code DouyinApiSpec}， 本类只表达业务语义。
 *
 * <p><b>为什么把渠道原始码带回</b>：抖音商品必须先挂类目/品牌资质才能上架，资质类拒绝是最高频的渠道失败。 把渠道 {@code code/message} 原样带回（{@link
 * ChannelListingResult#fail}）让运营能按码定位；统一成一句「上架失败」等于把可诊断信息扔掉。
 */
@Slf4j
@Extension(
    name = "DY_CHANNEL_PRODUCT_EXT",
    description = "抖音渠道商品上架/下架与库存同步（抖音电商开放平台）",
    tags = {"channel=DOUYIN"},
    weight = 100)
@RequiredArgsConstructor
public class DouyinProductExtension implements ExtensionChannelProductExtPoint {

  /** 渠道 SKU 前缀（抖音侧外部编码）。 */
  private static final String PREFIX = "DY";

  private static final String API_LIST_PRODUCT = "product.addV2";
  private static final String API_DELIST_PRODUCT = "product.offline";
  private static final String API_SYNC_STOCK = "product.updateStock";

  private static final String TITLE_REQUIRED = "TITLE_REQUIRED";
  private static final String PRICE_INVALID = "PRICE_INVALID";
  private static final String PRODUCT_ID_REQUIRED = "PRODUCT_ID_REQUIRED";

  private final ChannelOpenApiClient openApiClient;

  @Override
  public ChannelListingResult publishProduct(ChannelProductContext request) {
    if (request.productName() == null || request.productName().isBlank()) {
      return ChannelListingResult.fail(TITLE_REQUIRED, "抖音渠道要求商品标题非空");
    }
    if (request.listingPrice() == null || request.listingPrice().signum() <= 0) {
      return ChannelListingResult.fail(PRICE_INVALID, "抖音渠道要求挂牌价大于 0");
    }
    String outerId = PREFIX + request.productId();
    ChannelApiResult result =
        openApiClient.call(
            ChannelApiRequest.of("DOUYIN", API_LIST_PRODUCT, request.tenantId())
                .with("outer_product_id", outerId)
                .with("name", request.productName())
                .with("price", request.listingPrice().toPlainString()));
    if (!result.success()) {
      return ChannelListingResult.fail(
          result.errorCode(), "抖音渠道上架被拒: " + result.errorCode() + " " + result.errorMessage());
    }
    String channelProductId = ChannelJson.str(result.data(), "product.product_id");
    if (channelProductId == null || channelProductId.isBlank()) {
      channelProductId = outerId;
    }
    log.info("[DOUYIN] 商品上架完成 | product={} | productId={}", request.productId(), channelProductId);
    return ChannelListingResult.ok(channelProductId, "抖音渠道上架成功 product_id=" + channelProductId);
  }

  @Override
  public ChannelListingResult delistProduct(ChannelProductContext request) {
    if (request.channelProductId() == null || request.channelProductId().isBlank()) {
      return ChannelListingResult.fail(PRODUCT_ID_REQUIRED, "下架必须提供渠道商品ID");
    }
    ChannelApiResult result =
        openApiClient.call(
            ChannelApiRequest.of("DOUYIN", API_DELIST_PRODUCT, request.tenantId())
                .with("product_id", request.channelProductId()));
    if (!result.success()) {
      return ChannelListingResult.fail(
          result.errorCode(), "抖音渠道下架被拒: " + result.errorCode() + " " + result.errorMessage());
    }
    log.info("[DOUYIN] 商品下架完成 | productId={}", request.channelProductId());
    return ChannelListingResult.ok(null, "抖音渠道下架成功 product_id=" + request.channelProductId());
  }

  @Override
  public ChannelListingResult syncInventory(ChannelProductContext request) {
    if (request.channelProductId() == null || request.channelProductId().isBlank()) {
      return ChannelListingResult.fail(PRODUCT_ID_REQUIRED, "同步库存必须提供渠道商品ID");
    }
    int stock = request.stock() == null ? 0 : Math.max(0, request.stock());
    ChannelApiResult result =
        openApiClient.call(
            ChannelApiRequest.of("DOUYIN", API_SYNC_STOCK, request.tenantId())
                .with("product_id", request.channelProductId())
                .with("stock", String.valueOf(stock)));
    if (!result.success()) {
      return ChannelListingResult.fail(
          result.errorCode(), "抖音渠道库存同步被拒: " + result.errorCode() + " " + result.errorMessage());
    }
    log.info("[DOUYIN] 库存同步完成 | productId={} | stock={}", request.channelProductId(), stock);
    return ChannelListingResult.ok(
        request.channelProductId(),
        "抖音渠道库存已同步为 " + stock + " product_id=" + request.channelProductId());
  }
}
