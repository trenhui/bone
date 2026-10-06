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
 * 抖音电商渠道 · 物流扩展实现（发货回传 / 轨迹查询）。
 *
 * <p><b>接口映射</b>：发货回传【待核对】{@code order.ship}；轨迹查询【待核对】{@code order.trace.list}。 抖音以 {@code
 * logistics_id}（平台枚举）标识承运商， 内部物流名必须映射后提交， 否则渠道拒收。
 *
 * <p>MOCK 轨迹回落带 {@code [MOCK]} 标记，避免示例轨迹被当成真实物流轨迹。
 */
@Slf4j
@Extension(
    name = "DY_CHANNEL_LOGISTICS_EXT",
    description = "抖音渠道发货回传与物流轨迹查询（抖音电商开放平台）",
    tags = {"channel=DOUYIN"},
    weight = 100)
@RequiredArgsConstructor
public class DouyinLogisticsExtension implements ExtensionChannelFulfillmentExtPoint {

  /** 【待核对】抖音电商发货回传接口名需按当期文档核对。 */
  private static final String API_PUSH_SHIPMENT = "order.ship";

  /** 【待核对】抖音电商物流轨迹查询接口名需按当期文档核对。 */
  private static final String API_QUERY_TRACE = "order.trace.list";

  private static final String TRACKING_NO_REQUIRED = "TRACKING_NO_REQUIRED";

  private final ChannelOpenApiClient openApiClient;

  @Override
  public ChannelShipmentResult pushShipment(ChannelShipmentContext request) {
    if (request.trackingNo() == null || request.trackingNo().isBlank()) {
      return ChannelShipmentResult.fail(TRACKING_NO_REQUIRED, "抖音渠道回传必须提供运单号");
    }
    String logisticsId = mapLogisticsCompany(request.logisticsCompany());
    ChannelApiResult result =
        openApiClient.call(
            ChannelApiRequest.of("DOUYIN", API_PUSH_SHIPMENT, request.tenantId())
                .with("order_id", request.channelOrderNo())
                .with("logistics_id", logisticsId)
                .with("tracking_no", request.trackingNo()));
    if (!result.success()) {
      return ChannelShipmentResult.fail(
          result.errorCode(), "抖音渠道发货回传被拒: " + result.errorCode() + " " + result.errorMessage());
    }
    log.info(
        "[DOUYIN] 发货回传完成 | orderNo={} | company={} -> {} | trackingNo={}",
        request.channelOrderNo(),
        request.logisticsCompany(),
        logisticsId,
        request.trackingNo());
    return ChannelShipmentResult.ok(
        "抖音渠道已接收运单 " + request.trackingNo() + "（logistics_id=" + logisticsId + "）");
  }

  @Override
  public ChannelTraceResult queryTrace(ChannelShipmentContext request) {
    if (request.trackingNo() == null || request.trackingNo().isBlank()) {
      return ChannelTraceResult.fail(TRACKING_NO_REQUIRED, "查询轨迹必须提供运单号");
    }
    ChannelApiResult result =
        openApiClient.call(
            ChannelApiRequest.readOnly("DOUYIN", API_QUERY_TRACE, request.tenantId())
                .with("tracking_no", request.trackingNo()));
    if (!result.success()) {
      return ChannelTraceResult.fail(
          result.errorCode(), "抖音渠道轨迹查询失败: " + result.errorCode() + " " + result.errorMessage());
    }
    List<ChannelTraceResult.TraceNode> nodes = parseTrace(result.data());
    if (nodes.isEmpty()) {
      nodes = mockTraceNodes();
      log.info("[DOUYIN] 轨迹为 MOCK 示例数据 | trackingNo={}", request.trackingNo());
    }
    log.info("[DOUYIN] 轨迹查询完成 | trackingNo={} | nodes={}", request.trackingNo(), nodes.size());
    return ChannelTraceResult.ok(nodes);
  }

  /** 解析抖音轨迹结构（{@code trace_list[]}：{@code update_time}/{@code status_desc}）。 */
  private static List<ChannelTraceResult.TraceNode> parseTrace(Map<String, Object> data) {
    List<ChannelTraceResult.TraceNode> nodes = new ArrayList<>();
    for (Map<String, Object> trace : ChannelJson.list(data, "trace_list")) {
      String status = ChannelJson.str(trace, "status");
      nodes.add(
          new ChannelTraceResult.TraceNode(
              toInstant(ChannelJson.longOf(trace, "update_time")),
              status == null || status.isBlank() ? "IN_TRANSIT" : status,
              firstNonBlank(
                  ChannelJson.str(trace, "status_desc"),
                  ChannelJson.str(trace, "content"),
                  "抖音物流轨迹")));
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
            now.minus(2, ChronoUnit.DAYS), "COLLECTED", "[MOCK] 抖音渠道：包裹已被揽收"),
        new ChannelTraceResult.TraceNode(
            now.minus(1, ChronoUnit.DAYS), "IN_TRANSIT", "[MOCK] 抖音渠道：包裹运输中"),
        new ChannelTraceResult.TraceNode(
            now.minus(3, ChronoUnit.HOURS), "DELIVERING", "[MOCK] 抖音渠道：派送中"));
  }

  /** 内部物流公司名 → 抖音 logistics_id（顺丰 = 1，中通 = 2，圆通 = 3）。 */
  private static String mapLogisticsCompany(String internalName) {
    if (internalName == null || internalName.isBlank()) {
      return "1";
    }
    return switch (internalName.trim()) {
      case "顺丰速运", "SF", "SF EXPRESS" -> "1";
      case "中通快递", "ZTO" -> "2";
      case "圆通速递", "YTO" -> "3";
      default -> internalName.trim();
    };
  }
}
