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
 * 拼多多渠道 · 订单扩展实现（对接真实拼多多开放平台）。
 *
 * <p><b>接口映射</b>：拉单 {@code order.searchList}；发货回传 {@code order.logistics.add}（添加物流，接口名需按当期文档核对）。
 *
 * <p><b>拼多多协议要点</b>：错误不在根层而嵌在 {@code order_search_response.error_response} 内（由 {@code PddApiSpec}
 * 的兜底扫描处理）； 金额单位是 「分」；列表响应键是 {@code order_list}（顶层单元素 map 会被 {@code ChannelJson.list} 视作一个元素， 所以这里用
 * {@code "order_list"} 而不是 {@code "order_list.order"}）。
 *
 * <p><b>拉单时间窗</b>：拼多多按 {@code start_updated_at}/{@code end_updated_at}（Unix 秒）过滤，单窗有上限， 因此这里按「近 1
 * 天」默认窗口拉取， 由调度侧多次调用覆盖全量——写死一个不存在的 {@code orderId} 精确查询参数反而会在大商家侧漏单。
 */
@Slf4j
@Extension(
    name = "PDD_CHANNEL_ORDER_EXT",
    description = "拼多多渠道订单拉取归一化与状态回传（拼多多开放平台）",
    tags = {"channel=PDD"},
    weight = 100)
@RequiredArgsConstructor
public class PddOrderExtension implements ExtensionChannelOrderExtPoint {

  /** 渠道 SKU 前缀（拼多多侧外部编码格式）。 */
  private static final String SKU_PREFIX = "PDD";

  private static final String API_PULL_ORDER = "order.searchList";

  /** 拼多多订单状态：2 = 待发货。 */
  private static final String ORDER_STATUS_WAIT_SHIP = "2";

  /** 拉单默认时间窗（秒）：1 天。 */
  private static final long DEFAULT_WINDOW_SECONDS = 24 * 60 * 60L;

  /** 拼多多金额换算：分 → 元。 */
  private static final BigDecimal FEN_PER_YUAN = new BigDecimal("100");

  private final ChannelOpenApiClient openApiClient;

  @Override
  public ChannelOrderDraft pullOrder(ChannelOrderContext request) {
    long end = System.currentTimeMillis() / 1000L;
    ChannelApiResult result =
        openApiClient.call(
            ChannelApiRequest.of("PDD", API_PULL_ORDER, request.tenantId())
                .with("order_status", ORDER_STATUS_WAIT_SHIP)
                .with("start_updated_at", String.valueOf(end - DEFAULT_WINDOW_SECONDS))
                .with("end_updated_at", String.valueOf(end))
                .with("page", "1")
                .with("page_size", "50"));
    if (!result.success()) {
      throw BlueprintErrors.of(
          BlueprintErrorCodes.CHANNEL_OPENAPI_REJECTED,
          "拼多多渠道拉取订单被拒绝: " + result.errorCode() + " " + result.errorMessage());
    }

    List<ChannelOrderLine> lines = new ArrayList<>();
    BigDecimal freight = BigDecimal.ZERO;
    BigDecimal discount = BigDecimal.ZERO;
    for (Map<String, Object> order : ChannelJson.list(result.data(), "order_list")) {
      BigDecimal orderFreight = toYuan(ChannelJson.decimal(order, "logistics_fee"));
      BigDecimal orderDiscount = toYuan(ChannelJson.decimal(order, "discount_amount"));
      if (orderFreight != null) {
        freight = orderFreight;
      }
      if (orderDiscount != null) {
        discount = orderDiscount;
      }
      for (Map<String, Object> goods : ChannelJson.list(order, "goods_list")) {
        BigDecimal price = toYuan(ChannelJson.decimal(goods, "goods_price"));
        if (price == null) {
          price = toYuan(ChannelJson.decimal(goods, "goods_amount"));
        }
        lines.add(
            new ChannelOrderLine(
                outerSkuId(goods),
                firstNonBlank(
                    ChannelJson.str(goods, "goods_name"),
                    ChannelJson.str(goods, "goods_id"),
                    "拼多多商品"),
                quantity(goods),
                price == null ? BigDecimal.ZERO : price));
      }
    }
    if (lines.isEmpty()) {
      lines = request.lines();
    }
    if (lines.isEmpty()) {
      throw new IllegalArgumentException("拼多多渠道订单无有效明细: " + request.channelOrderNo());
    }

    ChannelOrderDraft draft =
        new ChannelOrderDraft(
            "PDD",
            request.channelOrderNo(),
            "MINI",
            toDraftLines(lines),
            freight,
            discount,
            request.receiverName(),
            request.receiverPhone(),
            request.receiverAddress());
    log.info(
        "[PDD] 拉单归一化完成 | orderNo={} | lines={} | amount={}",
        request.channelOrderNo(),
        draft.lines().size(),
        draft.totalAmount());
    return draft;
  }

  /**
   * 订单状态回传确认。
   *
   * <p><b>为什么不调渠道</b>：拼多多商家侧<strong>没有</strong>独立的「订单状态回传」接口，「已发货」是由添加物流（{@code
   * order.logistics.add}）驱动，而该调用属于发货能力（{@code PUSH_SHIPMENT}），已在 {@code
   * PddLogisticsExtension#pushShipment} 内实现。 若在这里
   * 再调一次添加物流，会产生重复运单——所以本方法只做幂等确认并说明真实回传路径，不编造一个不存在的接口。
   */
  @Override
  public boolean ackOrder(ChannelOrderContext request) {
    if (request.channelOrderNo() == null || request.channelOrderNo().isBlank()) {
      log.warn("[PDD] 回传失败：缺少渠道订单号");
      return false;
    }
    log.info("[PDD] 订单状态回传确认（已发货状态由 PUSH_SHIPMENT 添加物流驱动）| orderNo={}", request.channelOrderNo());
    return true;
  }

  private static BigDecimal toYuan(BigDecimal fen) {
    return fen == null ? null : fen.divide(FEN_PER_YUAN, 2, RoundingMode.HALF_UP);
  }

  /** 拼多多商品外部编码：{@code outer_product_id}/{@code outer_sku_id}，缺失时用前缀 + 商品ID 保证可逆。 */
  private static String outerSkuId(Map<String, Object> goods) {
    String outerProduct = ChannelJson.str(goods, "outer_product_id");
    if (outerProduct != null && !outerProduct.isBlank()) {
      return outerProduct;
    }
    String outerSku = ChannelJson.str(goods, "outer_sku_id");
    if (outerSku != null && !outerSku.isBlank()) {
      return outerSku;
    }
    return SKU_PREFIX + ChannelJson.str(goods, "goods_id");
  }

  /** 拼多多行数量：{@code goods_quantity} 缺失或非正时回落 1（偏小可被库存校验拦住，偏大造成超卖）。 */
  private static int quantity(Map<String, Object> goods) {
    Integer num = ChannelJson.intOf(goods, "goods_quantity");
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

  /** 从渠道 SKU 编码还原内部商品ID（格式 {@code PDD<数字>}）。 */
  private static Long parseProductId(String outerSkuId) {
    if (outerSkuId == null || outerSkuId.isBlank()) {
      throw new IllegalArgumentException("拼多多渠道 SKU 编码为空");
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
          "拼多多渠道 SKU 编码无法解析为内部商品ID: " + outerSkuId + "（期望格式 " + SKU_PREFIX + "<商品ID>）", ex);
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
