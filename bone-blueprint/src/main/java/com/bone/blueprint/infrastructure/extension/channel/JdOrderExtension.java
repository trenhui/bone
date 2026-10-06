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
 * 京东渠道 · 订单扩展实现（对接真实京东宙斯开放平台）。
 *
 * <p><b>接口映射</b>：拉单 {@code jos.order.search}；发货回传【待核对】（见常量注释）。
 *
 * <p><b>京东协议三个必须处理的差异（都由本类吸收）</b>：
 *
 * <ol>
 *   <li><b>金额单位是「分」</b>：{@code skuPrice}/{@code orderFreight} 都是分，直接塞进 {@link ChannelOrderDraft}
 *       会让订单金额放大 100 倍且<strong>不报错</strong>——这是接京东最典型的静默金额错，故统一走 {@link #toYuan}。
 *   <li><b>响应是二次 JSON</b>：{@code jd_response_content.result} 本身是 JSON 字符串，由 {@code JdApiSpec} 的
 *       {@code resultWrappedAsJson} 解开，本类读到的已是普通 map。
 *   <li><b>商品标识是 SKU 维度</b>：京东对外只给 {@code skuId}，与内部商品ID 无天然关系，靠外部编码 {@code JD<内部商品ID>} 可逆映射。
 * </ol>
 *
 * <p><b>为什么不用「渠道商品映射表」</b>：映射表是另一个聚合，在本聚合内查它会违反「一事务一聚合」。前缀编码把映射锁在扩展实现内， 主流程只见内部商品ID——这正是扩展点该干的事。
 */
@Slf4j
@Extension(
    name = "JD_CHANNEL_ORDER_EXT",
    description = "京东渠道订单拉取归一化与状态回传（宙斯开放平台）",
    tags = {"channel=JD"},
    weight = 100)
@RequiredArgsConstructor
public class JdOrderExtension implements ExtensionChannelOrderExtPoint {

  /** 渠道 SKU 前缀（京东侧外部编码格式）。 */
  private static final String SKU_PREFIX = "JD";

  private static final String API_PULL_ORDER = "jos.order.search";

  /** 【待核对】发货回传：京东以「配送单/运单」或「订单状态」表达已发货，接口名需按当期文档核对。 */
  private static final String API_ACK_ORDER = "jos.order.status.update";

  /** 京东金额换算：分 → 元。 */
  private static final BigDecimal FEN_PER_YUAN = new BigDecimal("100");

  private final ChannelOpenApiClient openApiClient;

  @Override
  public ChannelOrderDraft pullOrder(ChannelOrderContext request) {
    ChannelApiResult result =
        openApiClient.call(
            ChannelApiRequest.of("JD", API_PULL_ORDER, request.tenantId())
                .with("orderId", request.channelOrderNo())
                .with("page", "1")
                // pageSize 是京东宙斯 OpenAPI 的请求契约字段名（分页拉单），不是内部入参——
                // API 规范 §5.1 的 page/size 命名族只约束对外入参类，不约束三方渠道协议字段。
                .with("pageSize", "20"));
    if (!result.success()) {
      throw BlueprintErrors.of(
          BlueprintErrorCodes.CHANNEL_OPENAPI_REJECTED,
          "京东渠道拉取订单被拒绝: " + result.errorCode() + " " + result.errorMessage());
    }

    List<ChannelOrderLine> lines = new ArrayList<>();
    BigDecimal freight = BigDecimal.ZERO;
    BigDecimal discount = BigDecimal.ZERO;
    for (Map<String, Object> order : ChannelJson.list(result.data(), "order_list.order")) {
      BigDecimal orderFreight = toYuan(ChannelJson.decimal(order, "orderFreight"));
      BigDecimal orderDiscount = toYuan(ChannelJson.decimal(order, "orderDiscount"));
      if (orderFreight != null) {
        freight = orderFreight;
      }
      if (orderDiscount != null) {
        discount = orderDiscount;
      }
      for (Map<String, Object> line : ChannelJson.list(order, "orderLineList.orderLine")) {
        BigDecimal price = toYuan(ChannelJson.decimal(line, "skuPrice"));
        if (price == null) {
          price = toYuan(ChannelJson.decimal(line, "skuInfo.jdPrice"));
        }
        lines.add(
            new ChannelOrderLine(
                outerSkuId(line),
                firstNonBlank(
                    ChannelJson.str(line, "skuInfo.skuName"),
                    ChannelJson.str(line, "skuName"),
                    "京东商品"),
                quantity(line),
                price == null ? BigDecimal.ZERO : price));
      }
    }
    if (lines.isEmpty()) {
      lines = request.lines();
    }
    if (lines.isEmpty()) {
      throw new IllegalArgumentException("京东渠道订单无有效明细: " + request.channelOrderNo());
    }

    ChannelOrderDraft draft =
        new ChannelOrderDraft(
            "JD",
            request.channelOrderNo(),
            "WEB",
            toDraftLines(lines),
            freight,
            discount,
            request.receiverName(),
            request.receiverPhone(),
            request.receiverAddress());
    log.info(
        "[JD] 拉单归一化完成 | orderNo={} | lines={} | amount={}",
        request.channelOrderNo(),
        draft.lines().size(),
        draft.totalAmount());
    return draft;
  }

  @Override
  public boolean ackOrder(ChannelOrderContext request) {
    if (request.channelOrderNo() == null || request.channelOrderNo().isBlank()) {
      log.warn("[JD] 回传失败：缺少渠道订单号");
      return false;
    }
    ChannelApiResult result =
        openApiClient.call(
            ChannelApiRequest.of("JD", API_ACK_ORDER, request.tenantId())
                .with("orderId", request.channelOrderNo())
                .with("status", "WMS_SHIPPED"));
    if (!result.success()) {
      log.warn(
          "[JD] 发货回传被拒 | orderNo={} | code={} | msg={}",
          request.channelOrderNo(),
          result.errorCode(),
          result.errorMessage());
      return false;
    }
    log.info("[JD] 订单状态回传成功 | orderNo={}", request.channelOrderNo());
    return true;
  }

  /** 分 → 元；渠道给 {@code null} 时返回 {@code null} 以便调用方回落。 */
  private static BigDecimal toYuan(BigDecimal fen) {
    return fen == null ? null : fen.divide(FEN_PER_YUAN, 2, RoundingMode.HALF_UP);
  }

  /** 取渠道侧 SKU 编码：优先外部编码（{@code JD<内部商品ID>}），否则退回 SKU 数字。 */
  private static String outerSkuId(Map<String, Object> line) {
    String outerId = ChannelJson.str(line, "skuInfo.outerId");
    if (outerId != null && !outerId.isBlank()) {
      return outerId;
    }
    return SKU_PREFIX + ChannelJson.str(line, "skuId");
  }

  /**
   * 京东行数量。
   *
   * <p>【待核对】{@code order_line} 的「购买数量」字段名随文档版本变化，此处按 {@code orderLineNumId} 读取， 缺失时回落 1：
   * 数量偏小会被库存与金额 校验拦住，偏大则直接造成超卖。
   */
  private static int quantity(Map<String, Object> line) {
    Integer num = ChannelJson.intOf(line, "orderLineNumId");
    if (num != null && num > 0) {
      return num;
    }
    Integer fallback = ChannelJson.intOf(line, "skuInfo.orderLineNumId");
    return fallback == null || fallback <= 0 ? 1 : fallback;
  }

  private static String firstNonBlank(String... candidates) {
    for (String candidate : candidates) {
      if (candidate != null && !candidate.isBlank()) {
        return candidate;
      }
    }
    return "";
  }

  /** 从渠道 SKU 编码还原内部商品ID（格式 {@code JD<数字>}）。 */
  private static Long parseProductId(String outerSkuId) {
    if (outerSkuId == null || outerSkuId.isBlank()) {
      throw new IllegalArgumentException("京东渠道 SKU 编码为空");
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
          "京东渠道 SKU 编码无法解析为内部商品ID: " + outerSkuId + "（期望格式 " + SKU_PREFIX + "<商品ID>）", ex);
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
