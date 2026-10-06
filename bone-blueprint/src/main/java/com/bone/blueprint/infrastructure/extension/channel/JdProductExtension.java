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
 * 京东渠道 · 商品扩展实现（对接真实京东宙斯开放平台）。
 *
 * <p><b>接口映射</b>：上架 {@code ware.write.add}；下架与库存同步的接口名标注为 <b>【待核对】</b>（见下方常量注释）—— 京东宙斯
 * 商品侧接口多次改版，接入时必须按当期文档核对。之所以敢留「待核对」而不用猜的接口名：京东对不存在的接口返回的报文与「类目/资质错误」
 * 几乎一样，猜错时线上表现是「所有上架失败但看不出原因」，比明确标注更难排查。
 *
 * <p><b>换渠道只改本类</b>：签名（MD5、密钥只拼尾部）、公共参数、{@code jd_response_content.result} 的二次 JSON 解包全在 {@code
 * JdApiSpec} 与 {@link ChannelOpenApiClient} 里，换接口名不牵连应用层。
 */
@Slf4j
@Extension(
    name = "JD_CHANNEL_PRODUCT_EXT",
    description = "京东渠道商品上架/下架与库存同步（宙斯开放平台）",
    tags = {"channel=JD"},
    weight = 100)
@RequiredArgsConstructor
public class JdProductExtension implements ExtensionChannelProductExtPoint {

  /** 渠道 SKU 前缀（京东侧外部编码）。 */
  private static final String PREFIX = "JD";

  /** 京东新增商品。 */
  private static final String API_LIST_PRODUCT = "ware.write.add";

  /** 【待核对】商品下架：宙斯当期以「商品状态修改」表达上下架，接口名需按文档核对。 */
  private static final String API_DELIST_PRODUCT = "ware.write.update";

  /** 【待核对】库存同步：京东按 SKU 维度更新库存，接口名需按文档核对。 */
  private static final String API_SYNC_STOCK = "ware.stock.update";

  /** 京东商品状态：2 = 下架。 */
  private static final String WARE_STATUS_OFFLINE = "2";

  private static final String TITLE_REQUIRED = "TITLE_REQUIRED";
  private static final String PRICE_INVALID = "PRICE_INVALID";
  private static final String PRODUCT_ID_REQUIRED = "PRODUCT_ID_REQUIRED";

  private final ChannelOpenApiClient openApiClient;

  @Override
  public ChannelListingResult publishProduct(ChannelProductContext request) {
    if (request.productName() == null || request.productName().isBlank()) {
      return ChannelListingResult.fail(TITLE_REQUIRED, "京东渠道要求商品标题非空");
    }
    if (request.listingPrice() == null || request.listingPrice().signum() <= 0) {
      return ChannelListingResult.fail(PRICE_INVALID, "京东渠道要求挂牌价大于 0");
    }
    String outerId = PREFIX + request.productId();
    ChannelApiResult result =
        openApiClient.call(
            ChannelApiRequest.of("JD", API_LIST_PRODUCT, request.tenantId())
                .with("outer_id", outerId)
                .with("name", request.productName())
                .with("jd_price", request.listingPrice().toPlainString()));
    if (!result.success()) {
      return ChannelListingResult.fail(
          result.errorCode(), "京东渠道上架被拒: " + result.errorCode() + " " + result.errorMessage());
    }
    String channelProductId = ChannelJson.str(result.data(), "num_iid");
    if (channelProductId == null || channelProductId.isBlank()) {
      channelProductId = outerId;
    }
    log.info("[JD] 商品上架完成 | product={} | numIid={}", request.productId(), channelProductId);
    return ChannelListingResult.ok(channelProductId, "京东渠道上架成功 num_iid=" + channelProductId);
  }

  @Override
  public ChannelListingResult delistProduct(ChannelProductContext request) {
    if (request.channelProductId() == null || request.channelProductId().isBlank()) {
      return ChannelListingResult.fail(PRODUCT_ID_REQUIRED, "下架必须提供渠道商品ID");
    }
    ChannelApiResult result =
        openApiClient.call(
            ChannelApiRequest.of("JD", API_DELIST_PRODUCT, request.tenantId())
                .with("num_iid", request.channelProductId())
                .with("status", WARE_STATUS_OFFLINE));
    if (!result.success()) {
      return ChannelListingResult.fail(
          result.errorCode(), "京东渠道下架被拒: " + result.errorCode() + " " + result.errorMessage());
    }
    log.info("[JD] 商品下架完成 | numIid={}", request.channelProductId());
    return ChannelListingResult.ok(null, "京东渠道下架成功 num_iid=" + request.channelProductId());
  }

  @Override
  public ChannelListingResult syncInventory(ChannelProductContext request) {
    if (request.channelProductId() == null || request.channelProductId().isBlank()) {
      return ChannelListingResult.fail(PRODUCT_ID_REQUIRED, "同步库存必须提供渠道商品ID");
    }
    int stock = request.stock() == null ? 0 : Math.max(0, request.stock());
    ChannelApiResult result =
        openApiClient.call(
            ChannelApiRequest.of("JD", API_SYNC_STOCK, request.tenantId())
                .with("sku_id", request.channelProductId())
                .with("stock", String.valueOf(stock)));
    if (!result.success()) {
      return ChannelListingResult.fail(
          result.errorCode(), "京东渠道库存同步被拒: " + result.errorCode() + " " + result.errorMessage());
    }
    log.info("[JD] 库存同步完成 | skuId={} | stock={}", request.channelProductId(), stock);
    return ChannelListingResult.ok(
        request.channelProductId(),
        "京东渠道库存已同步为 " + stock + " sku_id=" + request.channelProductId());
  }
}
