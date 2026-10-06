package com.bone.blueprint.infrastructure.extension.channel;

import com.bone.blueprint.common.BlueprintErrorCodes;
import com.bone.blueprint.common.BlueprintErrors;
import com.bone.blueprint.domain.extension.channel.ChannelOrderContext;
import com.bone.blueprint.domain.extension.channel.ChannelOrderDraft;
import com.bone.blueprint.domain.extension.channel.ChannelOrderLine;
import com.bone.blueprint.infrastructure.channel.openapi.ChannelApiRequest;
import com.bone.blueprint.infrastructure.channel.openapi.ChannelApiResult;
import com.bone.blueprint.infrastructure.channel.openapi.ChannelJson;
import com.bone.blueprint.infrastructure.channel.openapi.ChannelOpenApiClient;
import com.bone.engine.extension.api.annotation.Extension;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 抖音电商渠道 · 订单扩展实现（对接真实抖音电商开放平台）。
 *
 * <p><b>接口映射</b>：拉单 {@code order.searchList}；发货回传【待核对】（见常量注释）。
 *
 * <p><b>抖音与其它三家的差异</b>：
 *
 * <ol>
 *   <li>信封是 {@code {"code":0,"data":{...}}}，{@code code != 0} 即业务失败——由 {@code DouyinApiSpec}
 *       归一，本类只看 {@link ChannelApiResult#success()}。
 *   <li>金额单位「分」，需换算（{@link #toYuan}）。
 *   <li><b>订单内不返回购买数量</b>：抖音的 {@code product_list} 一行即一个商品，结构上没有数量字段。故数量恒为 1， 并在日志中说明来源， 而不是编一个
 *       {@code num} 字段出来。
 * </ol>
 *
 * <p><b>MOCK 回落</b>：MOCK 通道无业务体，用调用方传入的 {@code request.lines()} 归一化，保证本地联调与 CI
 * 与真实通道走<strong>同一条</strong> 归一化代码路径。
 */
@Slf4j
@Extension(
    name = "DY_CHANNEL_ORDER_EXT",
    description = "抖音渠道订单拉取归一化与状态回传（抖音电商开放平台）",
    tags = {"channel=DOUYIN"},
    weight = 100)
@RequiredArgsConstructor
public class DouyinOrderExtension implements ExtensionChannelOrderExtPoint {

  /** 渠道 SKU 前缀（抖音侧外部编码格式）。 */
  private static final String SKU_PREFIX = "DY";

  private static final String API_PULL_ORDER = "order.searchList";

  /** 【待核对】发货回传：抖音电商以「订单发货」接口表达已发货，接口名需按当期文档核对。 */
  private static final String API_ACK_ORDER = "order.ship";

  /** 抖音订单状态：2 = 待发货。 */
  private static final String ORDER_STATUS_WAIT_SHIP = "2";

  /** 抖音金额换算：分 → 元。 */
  private static final BigDecimal FEN_PER_YUAN = new BigDecimal("100");

  private final ChannelOpenApiClient openApiClient;

  @Override
  public ChannelOrderDraft pullOrder(ChannelOrderContext request) {
    ChannelApiResult result =
        openApiClient.call(
            ChannelApiRequest.of("DOUYIN", API_PULL_ORDER, request.tenantId())
                .with("order_status", ORDER_STATUS_WAIT_SHIP)
                .with("page", "1")
                .with("page_size", "20"));
    if (!result.success()) {
      throw BlueprintErrors.of(
          BlueprintErrorCodes.CHANNEL_OPENAPI_REJECTED,
          "抖音渠道拉取订单被拒绝: " + result.errorCode() + " " + result.errorMessage());
    }

    List<ChannelOrderLine> lines = new ArrayList<>();
    BigDecimal freight = BigDecimal.ZERO;
    BigDecimal discount = BigDecimal.ZERO;
    for (Map<String, Object> order : ChannelJson.list(result.data(), "shop_order_list")) {
      BigDecimal orderFreight = toYuan(ChannelJson.decimal(order, "logistics_amount"));
      BigDecimal orderDiscount = toYuan(ChannelJson.decimal(order, "discount_amount"));
      if (orderFreight != null) {
        freight = orderFreight;
      }
      if (orderDiscount != null) {
        discount = orderDiscount;
      }
      for (Map<String, Object> product : ChannelJson.list(order, "product_list")) {
        BigDecimal price = toYuan(ChannelJson.decimal(product, "order_amount"));
        if (price == null) {
          price = toYuan(ChannelJson.decimal(product, "price"));
        }
        lines.add(
            new ChannelOrderLine(
                outerSkuId(product),
                firstNonBlank(
                    ChannelJson.str(product, "product_name"),
                    ChannelJson.str(product, "sku_id"),
                    "抖音商品"),
                quantity(product),
                price == null ? BigDecimal.ZERO : price));
      }
    }
    if (lines.isEmpty()) {
      lines = request.lines();
    }
    if (lines.isEmpty()) {
      throw new IllegalArgumentException("抖音渠道订单无有效明细: " + request.channelOrderNo());
    }

    ChannelOrderDraft draft =
        new ChannelOrderDraft(
            "DOUYIN",
            request.channelOrderNo(),
            "MINI",
            toDraftLines(lines),
            freight,
            discount,
            request.receiverName(),
            request.receiverPhone(),
            request.receiverAddress());
    log.info(
        "[DOUYIN] 拉单归一化完成 | orderNo={} | lines={} | amount={}",
        request.channelOrderNo(),
        draft.lines().size(),
        draft.totalAmount());
    return draft;
  }

  @Override
  public boolean ackOrder(ChannelOrderContext request) {
    if (request.channelOrderNo() == null || request.channelOrderNo().isBlank()) {
      log.warn("[DOUYIN] 回传失败：缺少渠道订单号");
      return false;
    }
    ChannelApiResult result =
        openApiClient.call(
            ChannelApiRequest.of("DOUYIN", API_ACK_ORDER, request.tenantId())
                .with("order_id", request.channelOrderNo())
                .with("logistics_id", "DouyinExpress"));
    if (!result.success()) {
      log.warn(
          "[DOUYIN] 发货回传被拒 | orderNo={} | code={} | msg={}",
          request.channelOrderNo(),
          result.errorCode(),
          result.errorMessage());
      return false;
    }
    log.info("[DOUYIN] 订单状态回传成功 | orderNo={}", request.channelOrderNo());
    return true;
  }

  private static BigDecimal toYuan(BigDecimal fen) {
    return fen == null ? null : fen.divide(FEN_PER_YUAN, 2, RoundingMode.HALF_UP);
  }

  /** 抖音商品外部编码：{@code outer_sku_id}/{@code outer_product_id}，缺失时用前缀 + 商品ID 保证可逆。 */
  private static String outerSkuId(Map<String, Object> product) {
    String outerSku = ChannelJson.str(product, "outer_sku_id");
    if (outerSku != null && !outerSku.isBlank()) {
      return outerSku;
    }
    String outerProduct = ChannelJson.str(product, "outer_product_id");
    if (outerProduct != null && !outerProduct.isBlank()) {
      return outerProduct;
    }
    return SKU_PREFIX + ChannelJson.str(product, "product_id");
  }

  /**
   * 抖音行数量。
   *
   * <p>抖音订单结构不提供购买数量（一个 {@code product_list} 元素即一件），故恒为 1； 渠道若后续补了数量字段，这里优先读 {@code quantity}。
   */
  private static int quantity(Map<String, Object> product) {
    Integer num = ChannelJson.intOf(product, "quantity");
    return num == null || num <= 0 ? 1 : num;
  }

  private static String firstNonBlank(String... candidates) {
    for (String candidate : candidates) {
      if (candidate != null && !candidate.isBlank()) {
        return candidate;
      }
    }
    return "";
  }

  /** 从渠道 SKU 编码还原内部商品ID（格式 {@code DY<数字>}）。 */
  private static Long parseProductId(String outerSkuId) {
    if (outerSkuId == null || outerSkuId.isBlank()) {
      throw new IllegalArgumentException("抖音渠道 SKU 编码为空");
    }
    String trimmed = outerSkuId.trim();
    String body =
        trimmed.toUpperCase().startsWith(SKU_PREFIX)
            ? trimmed.substring(SKU_PREFIX.length())
            : trimmed;
    try {
      return Long.parseLong(body);
    } catch (NumberFormatException ex) {
      throw new IllegalArgumentException(
          "抖音渠道 SKU 编码无法解析为内部商品ID: " + outerSkuId + "（期望格式 " + SKU_PREFIX + "<商品ID>）", ex);
    }
  }

  private List<ChannelOrderDraft.ChannelDraftLine> toDraftLines(List<ChannelOrderLine> lines) {
    List<ChannelOrderDraft.ChannelDraftLine> draftLines = new ArrayList<>();
    for (ChannelOrderLine line : lines) {
      draftLines.add(
          new ChannelOrderDraft.ChannelDraftLine(
              parseProductId(line.outerSkuId()), line.title(), line.quantity(), line.unitPrice()));
    }
    return draftLines;
  }
}
