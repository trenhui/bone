package com.bone.claim.infrastructure.client;

import com.bone.ai.gateway.application.LlmGatewayApplicationService;
import com.bone.ai.gateway.config.LlmGatewayProperties;
import com.bone.ai.gateway.domain.LlmMessage;
import com.bone.ai.gateway.domain.LlmRequest;
import com.bone.ai.gateway.domain.LlmResponse;
import com.bone.claim.application.port.out.AiProviderProbePort;
import com.bone.claim.domain.model.decision.AiDegradationReason;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * 模型 provider 探活实现：借网关门面做一次最小真实调用，把「配置态」升级为「运行态」事实。
 *
 * <p>与 {@link AiGatewayPortAdapter} 是同一防腐层的两个入口：业务入口 {@code assess} 拼业务 prompt、解析业务契约；探活入口只问 「你能不能按
 * JSON 回一句话」。两者共用同一套失败分类（{@link AiGatewayPortAdapter#classify}），避免「探活说超时、业务说网关错误」
 * 这类同源异判——分类逻辑一旦分叉，运维就得同时信两套结论。
 *
 * <p><b>为什么探活也要校验 JSON 契约</b>：只验证 TCP 可达等于只验证了一半。真实事故里最常见的是「provider 通了，但模型不按 JSON
 * 回」——这种环境下探活显示绿灯、业务全量降级，是最坏的组合。故探针必须把 {@code OUTPUT_CONTRACT_VIOLATION} 也算红灯。
 */
@Component
public class AiProviderProbeAdapter implements AiProviderProbePort {

  /**
   * 探活专用场景标签：与业务场景 {@code claim-ai-assess} 分开，成本报表可按场景剔除探活开销。
   *
   * <p>不共用业务标签是刻意的：共用会让「每次探活都记一笔核赔调用的账」，成本口径被探活频率污染，且模型档位路由会把探活当业务流量。
   */
  static final String PROBE_SCENARIO = "claim-ai-provider-probe";

  /** 探活逻辑别名：上游不回传模型标识时用它兜底，绝不返回空串（与业务侧 {@code resolvedModel} 同纪律）。 */
  static final String PROBE_MODEL_ALIAS = "synap-claim-probe";

  /** 最小化探活 prompt：要求一个固定形状的 JSON，token 消耗个位数。 */
  static final String PROBE_PROMPT = "Return exactly this JSON and nothing else: {\"ok\":true}";

  private final LlmGatewayApplicationService gateway;
  private final LlmGatewayProperties properties;
  private final ObjectMapper objectMapper = new ObjectMapper();

  public AiProviderProbeAdapter(
      LlmGatewayApplicationService gateway, LlmGatewayProperties properties) {
    this.gateway = gateway;
    this.properties = properties;
  }

  @Override
  public ProviderProbe probe() {
    if (!configured()) {
      // 未配 provider：网关会回落 MockLlmClient。此时发请求既浪费一次调用，又会把「没配」误报成「通」——
      // 直接给配置态结论，且不触碰网关。
      return new ProviderProbe(false, false, null, 0L, null);
    }
    long startedAt = System.nanoTime();
    LlmResponse response;
    try {
      response = gateway.invoke(request());
    } catch (RuntimeException ex) {
      return new ProviderProbe(true, false, null, elapsedMs(startedAt), classifyName(ex));
    }
    long latencyMs = elapsedMs(startedAt);
    JsonNode node;
    try {
      node = objectMapper.readTree(response.content());
    } catch (Exception ex) {
      // 返回了内容但不是 JSON：链路通、契约不通。这是「配了且通但业务全降级」的典型形态，必须红灯。
      return new ProviderProbe(
          true, false, null, latencyMs, AiDegradationReason.OUTPUT_CONTRACT_VIOLATION.name());
    }
    if (node == null || node.path("mock").asBoolean(false)) {
      // 配了 base-url 却仍拿到替身响应 ⇒ provider 未真正生效（装配缺失 / 密钥占位符未解析）。
      // 与业务侧 {@code AiGatewayPortAdapter.parse} 同一判据：替身绝不能被当成可用链路。
      return new ProviderProbe(
          true, false, null, latencyMs, AiDegradationReason.MOCK_NOT_ALLOWED.name());
    }
    return new ProviderProbe(true, true, resolvedModel(response), latencyMs, null);
  }

  private boolean configured() {
    String baseUrl = properties.getBaseUrl();
    return baseUrl != null && !baseUrl.isBlank();
  }

  private LlmRequest request() {
    return new LlmRequest(
        properties.getModel(),
        List.of(LlmMessage.user(PROBE_PROMPT)),
        null,
        null,
        true, // jsonResponse：探活要验证的正是结构化输出契约
        false,
        "probe-" + UUID.randomUUID(),
        PROBE_SCENARIO);
  }

  /** 与业务侧同一判据：上游模型标识缺失时回落逻辑别名，避免「通了但说不清是哪个模型」。 */
  private static String resolvedModel(LlmResponse response) {
    String model = response.model();
    return model == null || model.isBlank() ? PROBE_MODEL_ALIAS : model;
  }

  /** 复用业务适配器的异常链分类，保证探活与业务对同一故障给出同一个码。 */
  private static String classifyName(RuntimeException ex) {
    AiDegradationReason reason = AiGatewayPortAdapter.classify(ex);
    return reason.name();
  }

  private static long elapsedMs(long startedAt) {
    return (System.nanoTime() - startedAt) / 1_000_000L;
  }
}
