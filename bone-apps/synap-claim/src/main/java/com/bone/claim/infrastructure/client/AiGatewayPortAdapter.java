package com.bone.claim.infrastructure.client;

import com.bone.ai.gateway.application.LlmGatewayApplicationService;
import com.bone.ai.gateway.domain.LlmMessage;
import com.bone.ai.gateway.domain.LlmRequest;
import com.bone.ai.gateway.domain.LlmResponse;
import com.bone.claim.application.port.out.AiGatewayPort;
import com.bone.claim.application.port.out.AiGatewayUnavailableException;
import com.bone.claim.application.port.out.ClaimContextPort;
import com.bone.claim.domain.security.RejectReasonCodes;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * AI 评估网关适配器（Phase B：复用 bone-ai C5 LLM 网关治理门面）。
 *
 * <p>彻底移除一期本地确定性桩里"自研"的 AI 评估语义，改为委托 {@link LlmGatewayApplicationService}—— 出站 PII 脱敏（OWASP
 * LLM02）、提示注入检测（OWASP LLM01）、结构化输出校验、证据链（LlmCallRecorded）、 成本红线与配额全部由平台网关统一收口（ADR-002 / 能力图谱 §7.2
 * 安全三件套复用 C8）。 synap-claim 只负责"拼 prompt + 解析结构化结论"，不碰任何安全 / 治理实现。
 *
 * <p>网关未配置真实 provider（{@code bone.ai.gateway.base-url}）时自动回落网关内置 MockLlmClient， 返回自然语言；本适配器解析失败时按
 * fail-closed 抛 {@link AiGatewayUnavailableException}， 决策图对 LLM 节点降级 FALLBACK（绝不拿非结构化 /
 * 不可信输出做自动赔付结论）。
 *
 * <p>接线位置（ADR-002/§3.2.1）：bone-ai 实现类只在此 {@code infrastructure.client} 包被引入， 业务层经 {@link
 * AiGatewayPort} 防腐层消费，杜绝"绕过防腐层的偷接"。
 */
@Component
public class AiGatewayPortAdapter implements AiGatewayPort {

  /** 业务场景标签：供网关 {@code ModelRouter} 做模型档位路由（简单场景走廉价模型）。 */
  static final String SCENARIO = "claim-ai-assess";

  static final String MODEL = "synap-claim-ai";

  /**
   * 提示词契约版本：改动 system prompt 的结构化字段集（含拒赔原因码枚举）必须同步 bump。
   *
   * <p>决策复算要求"回到当时那套配置"——同一案件用新提示词重跑会得到不同结论，故提示词版本属审计必留字段 （V5 {@code
   * claim_decision_execution.prompt_version} 注释即"回放与复算必需"）。
   */
  static final String PROMPT_VERSION = "claim-assess-v2";

  private final LlmGatewayApplicationService gateway;
  private final ObjectMapper objectMapper = new ObjectMapper();

  public AiGatewayPortAdapter(LlmGatewayApplicationService gateway) {
    this.gateway = gateway;
  }

  @Override
  public AiAssessment assess(ClaimContextPort.ClaimContextView ctx) {
    String prompt = buildPrompt(ctx);
    LlmRequest request =
        new LlmRequest(
            MODEL,
            List.of(
                LlmMessage.system(
                    "你是保险理赔医学评估助手。仅依据给定案件事实判断是否需要拒赔建议，"
                        + "并以严格 JSON 输出，字段：rejectSuggested(boolean)、confidence(number,0~1)、"
                        + "rejectReasons(array of string，仅当 rejectSuggested=true 时给出，最多3个，"
                        + "取值严格限定于 [EVIDENCE_INSUFFICIENT, CLINICAL_UNREASONABLE, POLICY_EXCLUSION,"
                        + " AMOUNT_MISMATCH, FRAUD_SUSPECTED, OTHER]；不在清单内的码一律不得输出)。"
                        + "不要输出任何解释性自由文本；rejectReasons 只取上述枚举码，禁止臆造。"),
                LlmMessage.user(prompt)),
            null,
            null,
            true, // jsonResponse：要求结构化输出（网关 JsonOutputValidator 可校验）
            false,
            ctx.claimId() + "-" + UUID.randomUUID(),
            SCENARIO);
    LlmResponse response;
    try {
      response = gateway.invoke(request);
    } catch (RuntimeException ex) {
      // 网关任何异常（不可用 / 红线 / 配额 / 内容安全）→ 统一 fail-closed 降级
      throw new AiGatewayUnavailableException("AI 网关评估失败，降级纯规则路径: " + ex.getMessage(), ex);
    }
    // 模型未遵循结构化输出契约 → 视为不可信，fail-closed 降级
    return parse(response);
  }

  private AiAssessment parse(LlmResponse response) {
    try {
      JsonNode node = objectMapper.readTree(response.content());
      boolean reject = node.path("rejectSuggested").asBoolean(false);
      BigDecimal confidence = BigDecimal.valueOf(node.path("confidence").asDouble(0.5d));
      if (confidence.compareTo(BigDecimal.ZERO) < 0 || confidence.compareTo(BigDecimal.ONE) > 0) {
        confidence = new BigDecimal("0.5");
      }
      // 仅当建议拒赔时解析原因码，并经白名单归一化（未知码丢弃、空则兜底 OTHER）—— 保证"拒赔必有可解释原因"
      List<String> reasons =
          reject ? normalizeRejectReasons(node.path("rejectReasons")) : List.of();
      return new AiAssessment(
          true, reject, confidence, reasons, resolvedModel(response), PROMPT_VERSION);
    } catch (Exception ex) {
      throw new AiGatewayUnavailableException("AI 结构化结论解析失败，降级纯规则路径: " + ex.getMessage(), ex);
    }
  }

  /**
   * 真实上游模型标识：优先取网关回传的 {@code response.model()}（ModelRouter 实际路由到的模型）， 缺失时退回逻辑别名 {@link
   * #MODEL}——绝不返回空串，避免"AI 真的跑过但溯源字段为空"。
   */
  private static String resolvedModel(LlmResponse response) {
    String model = response.model();
    return model == null || model.isBlank() ? MODEL : model;
  }

  /** 从模型输出抽取拒赔原因码并白名单归一化（见 {@link RejectReasonCodes}）。 */
  private List<String> normalizeRejectReasons(JsonNode raw) {
    if (raw == null || !raw.isArray()) {
      return RejectReasonCodes.normalize(null);
    }
    List<Object> values = new ArrayList<>();
    raw.forEach(v -> values.add(v.isTextual() ? v.asText() : v.toString()));
    return RejectReasonCodes.normalize(values);
  }

  private String buildPrompt(ClaimContextPort.ClaimContextView ctx) {
    return "案件上下文（已脱敏）："
        + "claimId="
        + ctx.claimId()
        + ", claimType="
        + ctx.claimType()
        + ", claimAmount="
        + ctx.claimAmount()
        + ", hasEvidence="
        + ctx.hasEvidence()
        + ", evidenceAllParsed="
        + ctx.evidenceAllParsed()
        + ", minOcrConfidence="
        + ctx.minOcrConfidence()
        + ", evidenceAllVerifyPassed="
        + ctx.evidenceAllVerifyPassed();
  }
}
