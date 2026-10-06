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
 * 京东渠道 · 物流扩展实现（发货回传 / 轨迹查询）。
 *
 * <p><b>接口映射</b>：发货回传【待核对】{@code jos.waybill.create}（京东以「配送单」承载运单）； 轨迹查询【待核对】{@code
 * jos.track.query}。京东物流公司编码与内部不同（顺丰 → {@code JD_007}）， 直接用内部名渠道会拒收， 映射表因此留在扩展实现内。
 *
 * <p><b>MOCK 轨迹回落带 [MOCK] 标记</b>：不把示例轨迹伪装成真实轨迹。
 */
@Slf4j
@Extension(
    name = "JD_CHANNEL_LOGISTICS_EXT",
    description = "京东渠道发货回传与物流轨迹查询（宙斯开放平台）",
    tags = {"channel=JD"},
    weight = 100)
@RequiredArgsConstructor
public class JdLogisticsExtension implements ExtensionChannelLogisticsExtPoint {

  /** 【待核对】京东以「配送单创建」表达发货回传，接口名需按当期文档核对。 */
  private static final String API_PUSH_SHIPMENT = "jos.waybill.create";

  /** 【待核对】京东物流轨迹查询接口名需按当期文档核对。 */
  private static final String API_QUERY_TRACE = "jos.track.query";

  private static final String TRACKING_NO_REQUIRED = "TRACKING_NO_REQUIRED";

  private final ChannelOpenApiClient openApiClient;

  @Override
  public ChannelShipmentResult pushShipment(ChannelShipmentContext request) {
    if (request.trackingNo() == null || request.trackingNo().isBlank()) {
      return ChannelShipmentResult.fail(TRACKING_NO_REQUIRED, "京东渠道回传必须提供运单号");
    }
    String company = mapLogisticsCompany(request.logisticsCompany());
    ChannelApiResult result =
        openApiClient.call(
            ChannelApiRequest.of("JD", API_PUSH_SHIPMENT, request.tenantId())
                .with("orderId", request.channelOrderNo())
                .with("deliveryCompanyCode", company)
                .with("waybillCode", request.trackingNo()));
    if (!result.success()) {
      return ChannelShipmentResult.fail(
          result.errorCode(), "京东渠道发货回传被拒: " + result.errorCode() + " " + result.errorMessage());
    }
    log.info(
        "[JD] 发货回传完成 | orderNo={} | company={} -> {} | trackingNo={}",
        request.channelOrderNo(),
        request.logisticsCompany(),
        company,
        request.trackingNo());
    return ChannelShipmentResult.ok(
        "京东渠道已接收运单 " + request.trackingNo() + "（deliveryCompanyCode=" + company + "）");
  }

  @Override
  public ChannelTraceResult queryTrace(ChannelShipmentContext request) {
    if (request.trackingNo() == null || request.trackingNo().isBlank()) {
      return ChannelTraceResult.fail(TRACKING_NO_REQUIRED, "查询轨迹必须提供运单号");
    }
    ChannelApiResult result =
        openApiClient.call(
            ChannelApiRequest.of("JD", API_QUERY_TRACE, request.tenantId())
                .with("waybillCode", request.trackingNo()));
    if (!result.success()) {
      return ChannelTraceResult.fail(
          result.errorCode(), "京东渠道轨迹查询失败: " + result.errorCode() + " " + result.errorMessage());
    }
    List<ChannelTraceResult.TraceNode> nodes = parseTrace(result.data());
    if (nodes.isEmpty()) {
      nodes = mockTraceNodes();
      log.info("[JD] 轨迹为 MOCK 示例数据 | trackingNo={}", request.trackingNo());
    }
    log.info("[JD] 轨迹查询完成 | trackingNo={} | nodes={}", request.trackingNo(), nodes.size());
    return ChannelTraceResult.ok(nodes);
  }

  /** 解析京东轨迹结构（{@code trace.traces[]}：{@code acceptTime}/{@code context}）。 */
  private static List<ChannelTraceResult.TraceNode> parseTrace(Map<String, Object> data) {
    List<ChannelTraceResult.TraceNode> nodes = new ArrayList<>();
    for (Map<String, Object> trace : ChannelJson.list(data, "trace.traces")) {
      String time = ChannelJson.str(trace, "acceptTime");
      nodes.add(
          new ChannelTraceResult.TraceNode(
              parseEpochMillis(time), "IN_TRANSIT", ChannelJson.str(trace, "context")));
    }
    return nodes;
  }

  private static Instant parseEpochMillis(String text) {
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
            now.minus(2, ChronoUnit.DAYS), "COLLECTED", "[MOCK] 京东渠道：包裹已被揽收"),
        new ChannelTraceResult.TraceNode(
            now.minus(1, ChronoUnit.DAYS), "IN_TRANSIT", "[MOCK] 京东渠道：包裹运输中"),
        new ChannelTraceResult.TraceNode(
            now.minus(3, ChronoUnit.HOURS), "DELIVERING", "[MOCK] 京东渠道：派送中"));
  }

  /** 内部物流公司名 → 京东物流公司编码。 */
  private static String mapLogisticsCompany(String internalName) {
    if (internalName == null || internalName.isBlank()) {
      return "JD_007";
    }
    return switch (internalName.trim()) {
      case "顺丰速运", "SF" -> "JD_007";
      case "中通快递", "ZTO" -> "JD_ZTO";
      case "圆通速递", "YTO" -> "JD_YTO";
      default -> internalName.trim();
    };
  }
}
