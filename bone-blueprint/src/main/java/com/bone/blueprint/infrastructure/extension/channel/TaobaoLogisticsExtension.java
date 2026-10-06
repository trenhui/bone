package com.bone.blueprint.infrastructure.extension.channel;

import com.bone.blueprint.domain.extension.channel.ChannelShipmentContext;
import com.bone.blueprint.domain.extension.channel.ChannelShipmentResult;
import com.bone.blueprint.domain.extension.channel.ChannelTraceResult;
import com.bone.blueprint.infrastructure.channel.openapi.ChannelApiRequest;
import com.bone.blueprint.infrastructure.channel.openapi.ChannelApiResult;
import com.bone.blueprint.infrastructure.channel.openapi.ChannelJson;
import com.bone.blueprint.infrastructure.channel.openapi.ChannelOpenApiClient;
import com.bone.engine.extension.api.annotation.Extension;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 淘宝渠道 · 物流扩展实现（发货回传 / 轨迹查询）。
 *
 * <p><b>接口映射</b>：发货回传 {@code taobao.logistics.trace.publish}（TOP 用「物流轨迹上报」表达已发货，订单状态由运单号推导）； 轨迹查询
 * 【待核对】（TOP 未对卖家开放通用轨迹查询接口，见常量注释）。
 *
 * <p><b>物流公司编码是渠道专属知识</b>：内部叫「顺丰速运」，淘宝只认 {@code SF}。映射表放在扩展实现内而非应用服务， 是为了让主流程保持渠道无关——
 * 加第五个渠道时只多一个映射分支，不改任何编排代码。
 *
 * <p><b>MOCK 轨迹回落带 [MOCK] 标记</b>：本地/CI 无商家凭证时返回示例轨迹并显式标注， 避免「示例轨迹」被当成真实物流轨迹展示给运营。
 */
@Slf4j
@Extension(
    name = "TB_CHANNEL_LOGISTICS_EXT",
    description = "淘宝渠道发货回传与物流轨迹查询（TOP 开放平台）",
    tags = {"channel=TAOBAO"},
    weight = 100)
@RequiredArgsConstructor
public class TaobaoLogisticsExtension implements ExtensionChannelFulfillmentExtPoint {

  private static final String API_PUSH_SHIPMENT = "taobao.logistics.trace.publish";

  /** 【待核对】TOP 未对卖家开放通用轨迹查询接口，此处按「物流轨迹查询」占位，接入时需按开放平台能力核对。 */
  private static final String API_QUERY_TRACE = "taobao.logistics.trace.search";

  private static final String TRACKING_NO_REQUIRED = "TRACKING_NO_REQUIRED";

  private final ChannelOpenApiClient openApiClient;

  @Override
  public ChannelShipmentResult pushShipment(ChannelShipmentContext request) {
    if (request.trackingNo() == null || request.trackingNo().isBlank()) {
      return ChannelShipmentResult.fail(TRACKING_NO_REQUIRED, "淘宝渠道回传必须提供运单号");
    }
    String company = mapLogisticsCompany(request.logisticsCompany());
    ChannelApiResult result =
        openApiClient.call(
            ChannelApiRequest.of("TAOBAO", API_PUSH_SHIPMENT, request.tenantId())
                .with("tid", request.channelOrderNo())
                .with("company_name", company)
                .with("tracking_no", request.trackingNo()));
    if (!result.success()) {
      return ChannelShipmentResult.fail(
          result.errorCode(), "淘宝渠道发货回传被拒: " + result.errorCode() + " " + result.errorMessage());
    }
    log.info(
        "[TAOBAO] 发货回传完成 | orderNo={} | company={} -> {} | trackingNo={}",
        request.channelOrderNo(),
        request.logisticsCompany(),
        company,
        request.trackingNo());
    return ChannelShipmentResult.ok(
        "淘宝渠道已接收运单 " + request.trackingNo() + "（company_name=" + company + "）");
  }

  @Override
  public ChannelTraceResult queryTrace(ChannelShipmentContext request) {
    if (request.trackingNo() == null || request.trackingNo().isBlank()) {
      return ChannelTraceResult.fail(TRACKING_NO_REQUIRED, "查询轨迹必须提供运单号");
    }
    ChannelApiResult result =
        openApiClient.call(
            ChannelApiRequest.readOnly("TAOBAO", API_QUERY_TRACE, request.tenantId())
                .with("tid", request.channelOrderNo())
                .with("tracking_no", request.trackingNo()));
    if (!result.success()) {
      return ChannelTraceResult.fail(
          result.errorCode(), "淘宝渠道轨迹查询失败: " + result.errorCode() + " " + result.errorMessage());
    }
    List<ChannelTraceResult.TraceNode> nodes = parseTrace(result.data());
    if (nodes.isEmpty()) {
      // MOCK 通道无业务体：返回带标记的示例轨迹，保证本地联调可跑通且不会与真实轨迹混淆。
      nodes = mockTraceNodes();
      log.info("[TAOBAO] 轨迹为 MOCK 示例数据 | trackingNo={}", request.trackingNo());
    }
    log.info("[TAOBAO] 轨迹查询完成 | trackingNo={} | nodes={}", request.trackingNo(), nodes.size());
    return ChannelTraceResult.ok(nodes);
  }

  /** 解析 TOP 轨迹结构（{@code traces.trace[]}：{@code accept_time}/{@code content}）。 */
  private static List<ChannelTraceResult.TraceNode> parseTrace(Map<String, Object> data) {
    List<ChannelTraceResult.TraceNode> nodes = new ArrayList<>();
    for (Map<String, Object> trace : ChannelJson.list(data, "traces.trace")) {
      nodes.add(
          new ChannelTraceResult.TraceNode(
              toInstant(ChannelJson.str(trace, "accept_time")),
              "IN_TRANSIT",
              ChannelJson.str(trace, "content")));
    }
    return nodes;
  }

  private static Instant toInstant(String text) {
    if (text == null || text.isBlank()) {
      return Instant.now();
    }
    try {
      return Instant.ofEpochMilli(Long.parseLong(text.trim()));
    } catch (NumberFormatException ex) {
      return Instant.now();
    }
  }

  private static List<ChannelTraceResult.TraceNode> mockTraceNodes() {
    Instant now = Instant.now();
    return List.of(
        new ChannelTraceResult.TraceNode(
            now.minus(2, ChronoUnit.DAYS), "COLLECTED", "[MOCK] 淘宝渠道：包裹已被揽收"),
        new ChannelTraceResult.TraceNode(
            now.minus(1, ChronoUnit.DAYS), "IN_TRANSIT", "[MOCK] 淘宝渠道：包裹运输中"),
        new ChannelTraceResult.TraceNode(
            now.minus(3, ChronoUnit.HOURS), "DELIVERING", "[MOCK] 淘宝渠道：派送中"));
  }

  /** 内部物流公司名 → 淘宝物流公司编码（TOP 用 company_name）。 */
  private static String mapLogisticsCompany(String internalName) {
    if (internalName == null || internalName.isBlank()) {
      return "SF";
    }
    return switch (internalName.trim()) {
      case "顺丰速运", "SF", "SF EXPRESS" -> "SF";
      case "中通快递", "ZTO" -> "ZTO";
      case "圆通速递", "YTO" -> "YTO";
      case "申通快递", "STO" -> "STO";
      default -> internalName.trim();
    };
  }
}
