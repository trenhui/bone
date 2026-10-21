package com.bone.ai.gateway.infrastructure;

import com.bone.ai.gateway.domain.LlmClient;
import com.bone.ai.gateway.domain.LlmRequest;
import com.bone.ai.gateway.domain.LlmResponse;
import com.bone.ai.gateway.domain.gateway.ResponseConfidence;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 模型级联升档装饰器（廉价模型先行，低置信才升级强模型）——成本优化对位业界 model cascade。
 *
 * <p>先用 {@link #primary}（廉价档位）回答，经 {@link ResponseConfidence} 判定置信度；低于阈值才升级到 {@link #escalation}
 * （强档位）重问。绝大多数请求止步于廉价档位，仅「拿不准」的少数付出强档位成本——把「该用多贵的模型」按回答质量动态决定。
 *
 * <p><b>与降备援的分工</b>：{@link FallbackLlmClient} 治「调用失败换模型」，本装饰器治「回答不够好升级模型」。
 *
 * <p><b>升级失败回落</b>：强档位调用异常时返回廉价的低置信响应（fail-soft）——下游确定性闸门 / HITL 会因低置信转人工， 远比让整条链路因升级失败而硬崩更合理。
 */
public class CascadeLlmClient implements LlmClient {

  private static final Logger log = LoggerFactory.getLogger(CascadeLlmClient.class);

  private final LlmClient primary;
  private final LlmClient escalation;
  private final String escalationModel;
  private final double threshold;
  private final ResponseConfidence confidence;

  public CascadeLlmClient(
      LlmClient primary,
      LlmClient escalation,
      String escalationModel,
      double threshold,
      ResponseConfidence confidence) {
    this.primary = primary;
    this.escalation = escalation;
    this.escalationModel = escalationModel;
    this.threshold = threshold;
    this.confidence = confidence == null ? response -> -1 : confidence;
  }

  @Override
  public LlmResponse chat(LlmRequest request) {
    LlmResponse first = primary.chat(request);
    double conf = confidence.confidenceOf(first);
    // 无法判定（负数）不升级：凭猜测升级会导致双倍成本，交由下游闸门 / HITL 处理
    if (conf < 0 || conf >= threshold) {
      return first;
    }
    log.info(
        "级联升档: 廉价档位置信度 {} < 阈值 {}，升级 {} → {}", conf, threshold, request.model(), escalationModel);
    try {
      return escalation.chat(request.withModel(escalationModel));
    } catch (RuntimeException e) {
      log.warn("级联升档失败，回落低置信响应: {}", e.toString());
      return first;
    }
  }
}
