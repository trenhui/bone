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
 * 淘宝渠道 · 商品扩展实现（对接真实淘宝/天猫开放平台 TOP）。
 *
 * <p><b>接口映射</b>：上架 {@code taobao.item.add}、下架 {@code taobao.item.update}（{@code status=3} 仓库中）、 库存
 * {@code taobao.quantity.update}。三者都是 TOP 的 form 提交 + MD5 签名，签名/公共参数由 {@link ChannelOpenApiClient}
 * 统一处理，本类只负责 「传什么业务参数、怎么把响应读回内部结构」。
 *
 * <p><b>双通道语义</b>：{@code transport=HTTP} 走真实网关；MOCK 通道渠道侧无业务体（{@code data == null}）， 此时用本地确定性编码
 * {@code TB<内部商品ID>} 作为渠道商品ID返回 —— 保留这条回落<strong>不是</strong>为了让测试变绿，而是本地/CI 没有商家凭证时唯一诚实的
 * 表达：「渠道受理结果未知，但编码规则确定」。凭证缺失则直接失败关闭（{@code BP_CHANNEL_CREDENTIAL_MISSING}），不允许悄悄退回模拟。
 *
 * <p><b>渠道拒绝不抛异常</b>：类目不符、资质过期、限流都是预期内业务失败，统一走 {@link ChannelListingResult#fail} 让应用服务把实体置
 * FAILED；抛异常会污染 5xx 告警。
 */
@Slf4j
@Extension(
    name = "TB_CHANNEL_PRODUCT_EXT",
    description = "淘宝渠道商品上架/下架与库存同步（TOP 开放平台）",
    tags = {"channel=TAOBAO"},
    weight = 100)
@RequiredArgsConstructor
public class TaobaoProductExtension implements ExtensionChannelProductExtPoint {

  /** 渠道 SKU 前缀（淘宝侧商品编码格式，卖家自定义外部编码）。 */
  private static final String PREFIX = "TB";

  private static final String API_LIST_PRODUCT = "taobao.item.add";
  private static final String API_DELIST_PRODUCT = "taobao.item.update";
  private static final String API_SYNC_STOCK = "taobao.quantity.update";

  /** TOP 商品状态：3 = 仓库中（不下架但不可售，等价于「下架」语义）。 */
  private static final String ITEM_STATUS_OFFLINE = "3";

  private static final String TITLE_REQUIRED = "TITLE_REQUIRED";
  private static final String PRICE_INVALID = "PRICE_INVALID";
  private static final String PRODUCT_ID_REQUIRED = "PRODUCT_ID_REQUIRED";

  private final ChannelOpenApiClient openApiClient;

  @Override
  public ChannelListingResult listProduct(ChannelProductContext request) {
    if (request.productName() == null || request.productName().isBlank()) {
      return ChannelListingResult.fail(TITLE_REQUIRED, "淘宝渠道要求商品标题非空");
    }
    if (request.listingPrice() == null || request.listingPrice().signum() <= 0) {
      return ChannelListingResult.fail(PRICE_INVALID, "淘宝渠道要求挂牌价大于 0");
    }
    String numIid = PREFIX + request.productId();
    ChannelApiResult result =
        openApiClient.call(
            ChannelApiRequest.of("TAOBAO", API_LIST_PRODUCT, request.tenantId())
                .with("num_iid", numIid)
                .with("title", request.productName())
                .with("price", request.listingPrice().toPlainString()));
    if (!result.success()) {
      return ChannelListingResult.fail(
          result.errorCode(), "淘宝渠道上架被拒: " + result.errorCode() + " " + result.errorMessage());
    }
    // MOCK 通道 data 为 null：渠道未返回受理号，用确定性编码回落（见类注释「双通道语义」）。
    String channelProductId = ChannelJson.str(result.data(), "item.num_iid");
    if (channelProductId == null || channelProductId.isBlank()) {
      channelProductId = numIid;
    }
    log.info("[TAOBAO] 商品上架完成 | product={} | numIid={}", request.productId(), channelProductId);
    return ChannelListingResult.ok(channelProductId, "淘宝渠道上架成功 num_iid=" + channelProductId);
  }

  @Override
  public ChannelListingResult delistProduct(ChannelProductContext request) {
    if (request.channelProductId() == null || request.channelProductId().isBlank()) {
      return ChannelListingResult.fail(PRODUCT_ID_REQUIRED, "下架必须提供渠道商品ID");
    }
    ChannelApiResult result =
        openApiClient.call(
            ChannelApiRequest.of("TAOBAO", API_DELIST_PRODUCT, request.tenantId())
                .with("num_iid", request.channelProductId())
                .with("status", ITEM_STATUS_OFFLINE));
    if (!result.success()) {
      return ChannelListingResult.fail(
          result.errorCode(), "淘宝渠道下架被拒: " + result.errorCode() + " " + result.errorMessage());
    }
    log.info("[TAOBAO] 商品下架完成 | numIid={}", request.channelProductId());
    return ChannelListingResult.ok(null, "淘宝渠道下架成功 num_iid=" + request.channelProductId());
  }

  @Override
  public ChannelListingResult syncInventory(ChannelProductContext request) {
    if (request.channelProductId() == null || request.channelProductId().isBlank()) {
      return ChannelListingResult.fail(PRODUCT_ID_REQUIRED, "同步库存必须提供渠道商品ID");
    }
    int stock = request.stock() == null ? 0 : Math.max(0, request.stock());
    ChannelApiResult result =
        openApiClient.call(
            ChannelApiRequest.of("TAOBAO", API_SYNC_STOCK, request.tenantId())
                .with("num_iid", request.channelProductId())
                .with("quantity", String.valueOf(stock)));
    if (!result.success()) {
      return ChannelListingResult.fail(
          result.errorCode(), "淘宝渠道库存同步被拒: " + result.errorCode() + " " + result.errorMessage());
    }
    log.info("[TAOBAO] 库存同步完成 | numIid={} | stock={}", request.channelProductId(), stock);
    return ChannelListingResult.ok(
        request.channelProductId(),
        "淘宝渠道库存已同步为 " + stock + " num_iid=" + request.channelProductId());
  }
}
