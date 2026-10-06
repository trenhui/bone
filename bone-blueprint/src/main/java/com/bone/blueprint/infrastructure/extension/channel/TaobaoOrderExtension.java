package com.bone.blueprint.infrastructure.extension.channel;

import com.bone.blueprint.common.BlueprintErrorCodes;
import com.bone.blueprint.common.BlueprintErrors;
import com.bone.blueprint.domain.extension.channel.ChannelOrderContext;
import com.bone.blueprint.domain.extension.channel.ChannelOrderDraft;
import com.bone.blueprint.domain.extension.channel.ChannelOrderLine;
import com.bone.blueprint.domain.extension.channel.ChannelShipmentContext;
import com.bone.blueprint.infrastructure.channel.openapi.ChannelApiRequest;
import com.bone.blueprint.infrastructure.channel.openapi.ChannelApiResult;
import com.bone.blueprint.infrastructure.channel.openapi.ChannelJson;
import com.bone.blueprint.infrastructure.channel.openapi.ChannelOpenApiClient;
import com.bone.engine.extension.api.annotation.Extension;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;

/**
 * 淘宝渠道 · 订单扩展实现（对接真实淘宝/天猫开放平台 TOP）。
 *
 * <p><b>归一化要点</b>：淘宝订单明细的 {@code outer_item_id} 形如 {@code TB<内部商品ID>}（卖家自定义外部编码）， 剥前缀还原内部商品ID
 * 的动作在基类完成。这些差异若泄漏到下单服务，主流程就要为四个渠道各写一套解析——扩展点的意义正是把它们封在此处。
 *
 * <p><b>淘宝特有的两层报文</b>（其余三家是一层）：{@code trades.order[]} 拿到「交易(trade)」， {@code orders[]} 才是「订单明细」。且
 * <b>{@code price} 在 trade 层</b>而非 order 层， {@code title} 则优先取 order 层、缺失时回落 trade 层。
 *
 * <p><b>双通道</b>：{@code channel.openapi.transport=HTTP} 时调 {@code taobao.trade.orders.get} 真实拉单；
 * MOCK 时渠道无业务体，返回「成功 + 空」，本实现回落 {@code request} 上下文（本地联调与 CI 走这条）。 两条通道都<strong>必须</strong>归一化出同一个
 * {@link ChannelOrderDraft}——否则上线那天会出现「真实通道从未被测过」。
 *
 * <p><b>骨架说明</b>：调用、失败抛错、空明细回落、草稿装配、日志均由 {@link AbstractChannelOrderExtension} 承担，
 * 本类只提供渠道差异（请求参数、报文路径、金额单位、商品 ID 规则）。
 */
@Slf4j
@Extension(
    name = "TB_CHANNEL_ORDER_EXT",
    description = "淘宝渠道订单拉取归一化与状态回传（TOP 开放平台）",
    tags = {"channel=TAOBAO"},
    weight = 100)
public class TaobaoOrderExtension extends AbstractChannelOrderExtension
    implements ExtensionChannelOrderExtPoint {

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

  public TaobaoOrderExtension(ChannelOpenApiClient openApiClient) {
    super(log);
    this.openApiClient = openApiClient;
  }

  // ==================== 骨架提供的固定流程 ====================

  @Override
  public ChannelOrderDraft pullOrder(ChannelOrderContext request) {
    return pullOrderInternal(request);
  }

  @Override
  public boolean ackOrder(ChannelShipmentContext request) {
    if (request.channelOrderNo() == null || request.channelOrderNo().isBlank()) {
      log.warn("[{}] 回传失败：缺少渠道订单号", displayName());
      return false;
    }
    if (request.trackingNo() == null || request.trackingNo().isBlank()) {
      // 渠道会拿运单号去查真实物流轨迹，伪造单号等于把假货发给用户；宁可回传失败也不能编数据。
      log.warn("[{}] 回传失败：缺少运单号，拒绝伪造运单号回传 | orderNo={}", displayName(), request.channelOrderNo());
      return false;
    }
    String companyCode = ChannelLogisticsCodes.codeOf(channelCode(), request.logisticsCompany());
    if (companyCode == null) {
      log.warn(
          "[{}] 回传失败：物流公司未登记渠道编码（拒绝兜底为顺丰）| orderNo={} | company={}",
          displayName(),
          request.channelOrderNo(),
          request.logisticsCompany());
      return false;
    }
    ChannelApiResult result =
        openApiClient.call(
            ChannelApiRequest.of(channelCode(), API_ACK_ORDER, request.tenantId())
                .with("tid", request.channelOrderNo())
                .with("company_name", companyCode)
                .with("tracking_no", request.trackingNo()));
    if (!result.success()) {
      log.warn(
          "[{}] 发货回传被拒 | orderNo={} | code={} | msg={}",
          displayName(),
          request.channelOrderNo(),
          result.errorCode(),
          result.errorMessage());
      return false;
    }
    log.info("[{}] 订单状态回传成功 | orderNo={}", displayName(), request.channelOrderNo());
    return true;
  }

  // ==================== 渠道差异 ====================

  @Override
  protected String channelCode() {
    return "TAOBAO";
  }

  @Override
  protected String displayName() {
    return "淘宝";
  }

  @Override
  protected String channelSource() {
    return "WEB";
  }

  @Override
  protected String skuPrefix() {
    return SKU_PREFIX;
  }

  @Override
  protected ChannelApiResult buildCall(ChannelOrderContext request) {
    return openApiClient.call(
        ChannelApiRequest.readOnly(channelCode(), API_PULL_ORDER, request.tenantId())
            .with("page_no", "1")
            .with("page_size", "20")
            .with("status", "WAIT_SELLER_SEND_GOODS")
            .with("fields", ORDER_FIELDS));
  }

  @Override
  protected RuntimeException rejectionOf(ChannelApiResult result) {
    return BlueprintErrors.of(
        BlueprintErrorCodes.CHANNEL_OPENAPI_REJECTED,
        displayName() + "渠道拉取订单被拒绝: " + result.errorCode() + " " + result.errorMessage());
  }

  @Override
  protected NormalizedOrder extractOrder(Map<String, Object> data) {
    List<ChannelOrderLine> lines = new ArrayList<>();
    if (data == null) {
      return NormalizedOrder.of(lines);
    }
    for (Map<String, Object> trade : ChannelJson.list(data, "trades.order")) {
      // price 在 trade 层；title 优先 order 层、缺失回落 trade 层。
      String tradeTitle = ChannelJson.str(trade, "title");
      BigDecimal unitPrice = ChannelJson.decimal(trade, "price");
      for (Map<String, Object> order : ChannelJson.list(trade, "orders")) {
        String orderTitle = ChannelJson.str(order, "title");
        Integer num = ChannelJson.intOf(order, "num");
        lines.add(
            new ChannelOrderLine(
                ChannelJson.str(order, "outer_item_id"),
                orderTitle == null ? tradeTitle : orderTitle,
                num == null ? 0 : num,
                unitPrice == null ? BigDecimal.ZERO : unitPrice));
      }
    }
    // 淘宝 TOP 的运费/优惠在交易层，换算口径由渠道自身决定；此处按「无独立金额」处理。
    return NormalizedOrder.of(lines);
  }
}
