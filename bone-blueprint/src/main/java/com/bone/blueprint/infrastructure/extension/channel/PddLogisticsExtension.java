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
 * 拼多多渠道 · 物流扩展实现（发货回传 / 轨迹查询）。
 *
 * <p><b>接口映射</b>：发货回传 {@code order.logistics.add}（添加物流，接口名需按当期文档核对）； 轨迹查询 {@code
 * order.logistics.trace.list}。拼多多物流公司编码与内部不同（顺丰 → {@code PDD_SF}），映射表留在扩展实现内。
 *
 * <p>MOCK 轨迹回落带 {@code [MOCK]} 标记。
 */
@Slf4j
@Extension(
    name = "PDD_CHANNEL_LOGISTICS_EXT",
    description = "拼多多渠道发货回传与物流轨迹查询（拼多多开放平台）",
    tags = {"channel=PDD"},
    weight = 100)
@RequiredArgsConstructor
public class PddLogisticsExtension implements ExtensionChannelLogisticsExtPoint {

  private static final String API_PUSH_SHIPMENT = "order.logistics.add";
  private static final String API_QUERY_TRACE = "order.logistics.trace.list";

  private static final String TRACKING_NO_REQUIRED = "TRACKING_NO_REQUIRED";

  private final ChannelOpenApiClient openApiClient;

  @Override
  public ChannelShipmentResult pushShipment(ChannelShipmentContext request) {
    if (request.trackingNo() == null || request.trackingNo().isBlank()) {
      return ChannelShipmentResult.fail(TRACKING_NO_REQUIRED, "拼多多渠道回传必须提供运单号");
    }
    String company = mapLogisticsCompany(request.logisticsCompany());
    ChannelApiResult result =
        openApiClient.call(
            ChannelApiRequest.of("PDD", API_PUSH_SHIPMENT, request.tenantId())
                .with("order_sn", request.channelOrderNo())
                .with("tracking_number", request.trackingNo())
                .with("shipping_company", company));
    if (!result.success()) {
      return ChannelShipmentResult.fail(
          result.errorCode(), "拼多多渠道发货回传被拒: " + result.errorCode() + " " + result.errorMessage());
    }
    log.info(
        "[PDD] 发货回传完成 | orderNo={} | company={} -> {} | trackingNo={}",
        request.channelOrderNo(),
        request.logisticsCompany(),
        company,
        request.trackingNo());
    return ChannelShipmentResult.ok(
        "拼多多渠道已接收运单 " + request.trackingNo() + "（shipping_company=" + company + "）");
  }

  @Override
  public ChannelTraceResult queryTrace(ChannelShipmentContext request) {
    if (request.trackingNo() == null || request.trackingNo().isBlank()) {
      return ChannelTraceResult.fail(TRACKING_NO_REQUIRED, "查询轨迹必须提供运单号");
    }
    ChannelApiResult result =
        openApiClient.call(
            ChannelApiRequest.of("PDD", API_QUERY_TRACE, request.tenantId())
                .with("tracking_number", request.trackingNo()));
    if (!result.success()) {
      return ChannelTraceResult.fail(
          result.errorCode(), "拼多多渠道轨迹查询失败: " + result.errorCode() + " " + result.errorMessage());
    }
    List<ChannelTraceResult.TraceNode> nodes = parseTrace(result.data());
    if (nodes.isEmpty()) {
      nodes = mockTraceNodes();
      log.info("[PDD] 轨迹为 MOCK 示例数据 | trackingNo={}", request.trackingNo());
    }
    log.info("[PDD] 轨迹查询完成 | trackingNo={} | nodes={}", request.trackingNo(), nodes.size());
    return ChannelTraceResult.ok(nodes);
  }

  /** 解析拼多多轨迹结构（{@code trace_list[]}：{@code update_time}/{@code trace_content}）。 */
  private static List<ChannelTraceResult.TraceNode> parseTrace(Map<String, Object> data) {
    List<ChannelTraceResult.TraceNode> nodes = new ArrayList<>();
    for (Map<String, Object> trace : ChannelJson.list(data, "trace_list")) {
      nodes.add(
          new ChannelTraceResult.TraceNode(
              toInstant(ChannelJson.longOf(trace, "update_time")),
              "IN_TRANSIT",
              firstNonBlank(
                  ChannelJson.str(trace, "trace_content"),
                  ChannelJson.str(trace, "content"),
                  "拼多多物流轨迹")));
    }
    return nodes;
  }

  private static Instant toInstant(Long epochSeconds) {
    return epochSeconds == null ? Instant.now() : Instant.ofEpochSecond(epochSeconds);
  }

  private static String firstNonBlank(String... candidates) {
    for (String candidate : candidates) {
      if (candidate != null && !candidate.isBlank()) {
        return candidate;
      }
    }
    return "";
  }

  private static List<ChannelTraceResult.TraceNode> mockTraceNodes() {
    Instant now = Instant.now();
    return List.of(
        new ChannelTraceResult.TraceNode(
            now.minus(2, ChronoUnit.DAYS), "COLLECTED", "[MOCK] 拼多多渠道：包裹已被揽收"),
        new ChannelTraceResult.TraceNode(
            now.minus(1, ChronoUnit.DAYS), "IN_TRANSIT", "[MOCK] 拼多多渠道：包裹运输中"),
        new ChannelTraceResult.TraceNode(
            now.minus(3, ChronoUnit.HOURS), "DELIVERING", "[MOCK] 拼多多渠道：派送中"));
  }

  /** 内部物流公司名 → 拼多多物流公司编码。 */
  private static String mapLogisticsCompany(String internalName) {
    if (internalName == null || internalName.isBlank()) {
      return "PDD_SF";
    }
    return switch (internalName.trim()) {
      case "顺丰速运", "SF" -> "PDD_SF";
      case "中通快递", "ZTO" -> "PDD_ZTO";
      case "圆通速递", "YTO" -> "PDD_YTO";
      default -> internalName.trim();
    };
  }
}
