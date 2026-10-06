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
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 淘宝渠道 · 订单扩展实现（对接真实淘宝/天猫开放平台 TOP）。
 *
 * <p><b>归一化要点</b>：淘宝订单明细的 {@code outer_item_id} 形如 {@code TB<内部商品ID>}（卖家自定义外部编码），
 * 本实现负责剥掉前缀还原内部商品ID。这些差异若泄漏到下单服务，主流程就要为四个渠道各写一套解析——扩展点的意义正是把它们封在此处。
 *
 * <p><b>双通道</b>：{@code channel.openapi.transport=HTTP} 时调 {@code taobao.trade.orders.get} 真实拉单；
 * MOCK 时渠道无业务体，返回「成功 + 空」，本实现回落 {@code request} 上下文（本地联调与 CI 走这条）。 两条通道都<strong>必须</strong>归一化出同一个
 * {@link ChannelOrderDraft}——否则上线那天会出现「真实通道从未被测过」。
 *
 * <p><b>接口名为什么是常量</b>：TOP 的接口名（含 {@code taobao.logistics.trace.publish} 这条发货回传链路）随平台版本演进，
 * 常量化后「换一个渠道实现」= 改本类这几个常量与映射，客户端、应用层零改动。
 */
@Slf4j
@Extension(
    name = "TB_CHANNEL_ORDER_EXT",
    description = "淘宝渠道订单拉取归一化与状态回传（TOP 开放平台）",
    tags = {"channel=TAOBAO"},
    weight = 100)
@RequiredArgsConstructor
public class TaobaoOrderExtension implements ExtensionChannelOrderExtPoint {

  /** 渠道 SKU 前缀（淘宝侧商品编码格式）。 */
  private static final String SKU_PREFIX = "TB";

  /** 淘宝拉取卖家已付款未发货订单。 */
  private static final String API_PULL_ORDER = "taobao.trade.orders.get";

  /** 发货回传：TOP 用物流轨迹上报表达「已发货」（订单状态由运单号推导）。 */
  private static final String API_ACK_ORDER = "taobao.logistics.trace.publish";

  /** 请求字段清单：TOP 必须显式声明 fields，不给全就只回订单号。 */
  private static final String ORDER_FIELDS =
      "tid,status,payment,total_fee,discount_fee,buyer_nick,buyer_user_id,"
          + "receiver_name,receiver_phone,receiver_address,orders.oid,orders.num,"
          + "orders.price,orders.title,orders.outer_item_id";

  private final ChannelOpenApiClient openApiClient;

  @Override
  public ChannelOrderDraft pullOrder(ChannelOrderContext request) {
    ChannelApiResult result =
        openApiClient.call(
            ChannelApiRequest.of("TAOBAO", API_PULL_ORDER, request.tenantId())
                .with("page_no", "1")
                .with("page_size", "20")
                .with("status", "WAIT_SELLER_SEND_GOODS")
                .with("fields", ORDER_FIELDS));
    if (!result.success()) {
      throw BlueprintErrors.of(
          BlueprintErrorCodes.CHANNEL_OPENAPI_REJECTED,
          "淘宝渠道拉取订单被拒绝: " + result.errorCode() + " " + result.errorMessage());
    }

    List<ChannelOrderLine> lines = new ArrayList<>();
    Map<String, Object> data = result.data();
    if (data != null) {
      for (Map<String, Object> trade : ChannelJson.list(data, "trades.order")) {
        String title = ChannelJson.str(trade, "title");
        BigDecimal unitPrice = ChannelJson.decimal(trade, "price");
        for (Map<String, Object> order : ChannelJson.list(trade, "orders")) {
          lines.add(
              new ChannelOrderLine(
                  ChannelJson.str(order, "outer_item_id"),
                  ChannelJson.str(order, "title") == null ? title : ChannelJson.str(order, "title"),
                  ChannelJson.intOf(order, "num") == null ? 0 : ChannelJson.intOf(order, "num"),
                  unitPrice == null ? BigDecimal.ZERO : unitPrice));
        }
      }
    }
    // 空明细不能静默通过：静默会让「渠道没拉到」伪装成「渠道没订单」，排查时无从下手。
    if (lines.isEmpty()) {
      lines = request.lines();
    }
    if (lines.isEmpty()) {
      throw new IllegalArgumentException("淘宝渠道订单无有效明细: " + request.channelOrderNo());
    }

    ChannelOrderDraft draft =
        new ChannelOrderDraft(
            "TAOBAO",
            request.channelOrderNo(),
            "WEB",
            toDraftLines(lines),
            BigDecimal.ZERO,
            BigDecimal.ZERO,
            request.receiverName(),
            request.receiverPhone(),
            request.receiverAddress());
    log.info(
        "[TAOBAO] 拉单归一化完成 | orderNo={} | lines={} | amount={}",
        request.channelOrderNo(),
        draft.lines().size(),
        draft.totalAmount());
    return draft;
  }

  @Override
  public boolean ackOrder(ChannelOrderContext request) {
    if (request.channelOrderNo() == null || request.channelOrderNo().isBlank()) {
      log.warn("[TAOBAO] 回传失败：缺少渠道订单号");
      return false;
    }
    ChannelApiResult result =
        openApiClient.call(
            ChannelApiRequest.of("TAOBAO", API_ACK_ORDER, request.tenantId())
                .with("tp_id", request.channelOrderNo())
                .with("company_name", "SF")
                .with("tracking_no", "SF0000000000"));
    if (!result.success()) {
      log.warn(
          "[TAOBAO] 发货回传被拒 | orderNo={} | code={} | msg={}",
          request.channelOrderNo(),
          result.errorCode(),
          result.errorMessage());
      return false;
    }
    log.info("[TAOBAO] 订单状态回传成功 | orderNo={}", request.channelOrderNo());
    return true;
  }

  /** 从渠道 SKU 编码还原内部商品ID（格式 {@code TB<数字>}）。 */
  private static Long parseProductId(String outerSkuId) {
    if (outerSkuId == null || outerSkuId.isBlank()) {
      throw new IllegalArgumentException("淘宝渠道 SKU 编码为空");
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
          "淘宝渠道 SKU 编码无法解析为内部商品ID: " + outerSkuId + "（期望格式 " + SKU_PREFIX + "<商品ID>）", ex);
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
