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
 * 拼多多渠道 · 商品扩展实现（对接真实拼多多开放平台 gw-api.pinduoduo.com）。
 *
 * <p><b>接口映射</b>：上架 {@code goods.add}、上下架切换 {@code goods.update_status}、库存 {@code
 * goods.update_stock}。 拼多多的凭证参数名与 淘宝/京东不同（{@code client_id}/{@code client_secret}，且 {@code
 * client_secret} 只参与签名不进请求体）， 错误又嵌在 {@code goods_add_response.error_response} 里——这些都在 {@code
 * PddApiSpec} 与 {@link ChannelOpenApiClient} 内解决，本类不感知。
 *
 * <p><b>库存语义提醒</b>：拼多多库存是 SKU 维度的数量，覆盖式更新而非增量， 所以这里传的是<strong>目标库存</strong>（{@code
 * request.stock()}）， 不是 {@code ±变更量}；把增量当目标量传是接真实渠道最典型的超卖成因。
 */
@Slf4j
@Extension(
    name = "PDD_CHANNEL_PRODUCT_EXT",
    description = "拼多多渠道商品上架/下架与库存同步（拼多多开放平台）",
    tags = {"channel=PDD"},
    weight = 100)
@RequiredArgsConstructor
public class PddProductExtension implements ExtensionChannelProductExtPoint {

  /** 渠道 SKU 前缀（拼多多侧外部编码）。 */
  private static final String PREFIX = "PDD";

  private static final String API_LIST_PRODUCT = "goods.add";
  private static final String API_DELIST_PRODUCT = "goods.update_status";
  private static final String API_SYNC_STOCK = "goods.update_stock";

  /** 拼多多商品状态：2 = 下架。 */
  private static final String GOODS_STATUS_OFFLINE = "2";

  private static final String TITLE_REQUIRED = "TITLE_REQUIRED";
  private static final String PRICE_INVALID = "PRICE_INVALID";
  private static final String PRODUCT_ID_REQUIRED = "PRODUCT_ID_REQUIRED";

  private final ChannelOpenApiClient openApiClient;

  @Override
  public ChannelListingResult publishProduct(ChannelProductContext request) {
    if (request.productName() == null || request.productName().isBlank()) {
      return ChannelListingResult.fail(TITLE_REQUIRED, "拼多多渠道要求商品标题非空");
    }
    if (request.listingPrice() == null || request.listingPrice().signum() <= 0) {
      return ChannelListingResult.fail(PRICE_INVALID, "拼多多渠道要求挂牌价大于 0");
    }
    String outerId = PREFIX + request.productId();
    ChannelApiResult result =
        openApiClient.call(
            ChannelApiRequest.of("PDD", API_LIST_PRODUCT, request.tenantId())
                .with("outer_product_id", outerId)
                .with("goods_name", request.productName())
                .with("min_group_price", request.listingPrice().toPlainString()));
    if (!result.success()) {
      return ChannelListingResult.fail(
          result.errorCode(), "拼多多渠道上架被拒: " + result.errorCode() + " " + result.errorMessage());
    }
    String channelProductId = ChannelJson.str(result.data(), "goods_id");
    if (channelProductId == null || channelProductId.isBlank()) {
      channelProductId = outerId;
    }
    log.info("[PDD] 商品上架完成 | product={} | goodsId={}", request.productId(), channelProductId);
    return ChannelListingResult.ok(channelProductId, "拼多多渠道上架成功 goods_id=" + channelProductId);
  }

  @Override
  public ChannelListingResult delistProduct(ChannelProductContext request) {
    if (request.channelProductId() == null || request.channelProductId().isBlank()) {
      return ChannelListingResult.fail(PRODUCT_ID_REQUIRED, "下架必须提供渠道商品ID");
    }
    ChannelApiResult result =
        openApiClient.call(
            ChannelApiRequest.of("PDD", API_DELIST_PRODUCT, request.tenantId())
                .with("goods_id", request.channelProductId())
                .with("update_type", GOODS_STATUS_OFFLINE));
    if (!result.success()) {
      return ChannelListingResult.fail(
          result.errorCode(), "拼多多渠道下架被拒: " + result.errorCode() + " " + result.errorMessage());
    }
    log.info("[PDD] 商品下架完成 | goodsId={}", request.channelProductId());
    return ChannelListingResult.ok(null, "拼多多渠道下架成功 goods_id=" + request.channelProductId());
  }

  @Override
  public ChannelListingResult syncInventory(ChannelProductContext request) {
    if (request.channelProductId() == null || request.channelProductId().isBlank()) {
      return ChannelListingResult.fail(PRODUCT_ID_REQUIRED, "同步库存必须提供渠道商品ID");
    }
    int stock = request.stock() == null ? 0 : Math.max(0, request.stock());
    ChannelApiResult result =
        openApiClient.call(
            ChannelApiRequest.of("PDD", API_SYNC_STOCK, request.tenantId())
                .with("goods_id", request.channelProductId())
                .with("stock_quantity", String.valueOf(stock)));
    if (!result.success()) {
      return ChannelListingResult.fail(
          result.errorCode(), "拼多多渠道库存同步被拒: " + result.errorCode() + " " + result.errorMessage());
    }
    log.info("[PDD] 库存同步完成 | goodsId={} | stock={}", request.channelProductId(), stock);
    return ChannelListingResult.ok(
        request.channelProductId(),
        "拼多多渠道库存已同步为 " + stock + " goods_id=" + request.channelProductId());
  }
}
