package com.bone.claim.infrastructure.client;

import com.bone.ai.gateway.application.LlmGatewayApplicationService;
import com.bone.ai.gateway.domain.LlmMessage;
import com.bone.ai.gateway.domain.LlmRequest;
import com.bone.ai.gateway.domain.LlmResponse;
import com.bone.claim.application.port.out.AiGatewayPort;
import com.bone.claim.application.port.out.AiGatewayPort.Fact;
import com.bone.claim.application.port.out.AiGatewayUnavailableException;
import com.bone.claim.application.port.out.ClaimContextPort;
import com.bone.claim.domain.model.decision.AiDegradationReason;
import com.bone.claim.domain.model.decision.AiDiscrepancyCodes;
import com.bone.claim.domain.security.RejectReasonCodes;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.net.SocketTimeoutException;
import java.net.http.HttpTimeoutException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeoutException;
import org.springframework.stereotype.Component;

/**
 * AI 评估网关适配器（Phase B：复用 bone-ai C5 LLM 网关治理门面）。
 *
 * <p>彻底移除一期本地确定性桩里"自研"的 AI 评估语义，改为委托 {@link LlmGatewayApplicationService}—— 出站 PII 脱敏（OWASP
 * LLM02）、提示注入检测（OWASP LLM01）、结构化输出校验、证据链（LlmCallRecorded）、 成本红线与配额全部由平台网关统一收口（ADR-002 / 能力图谱 §7.2
 * 安全三件套复用 C8）。 synap-claim 只负责"拼 prompt + 解析结构化结论"，不碰任何安全 / 治理实现。
 *
 * <p>网关未配置真实 provider（{@code bone.ai.gateway.base-url}）时自动回落网关内置 MockLlmClient。 G0-2
 * 起该替身遵守结构化输出契约并自报 {@code mock:true}，本适配器<b>显式拒绝</b>替身响应，按 fail-closed 抛 {@link
 * AiGatewayUnavailableException}（消息携带可运维的修法指引），决策图对 LLM 节点降级 FALLBACK—— 绝不拿非结构化 / 不可信 /
 * 替身输出做自动赔付结论。
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
  // v4：差异提示不再只做留痕，白名单内的码会驱动非资金流程状态（见 AiDiscrepancyRouting），
  // 提示词契约随语义变化 bump，保证回放能区分「差异码仅展示」与「差异码参与路由」。
  static final String PROMPT_VERSION = "claim-assess-v4";

  /** 差异提示受控码白名单（唯一真源在领域层）：模型不得臆造自由文本结论。 */
  static final List<String> DISCREPANCY_CODES = AiDiscrepancyCodes.ALL;

  /** 认知层数组与字符串上限：防模型输出膨胀撑爆决策轨迹。 */
  static final int MAX_COGNITIVE_ITEMS = 20;

  static final int MAX_COGNITIVE_TEXT = 200;

  /** 异常链分类的最大遍历深度（防自引用 / 循环 cause 把分类逻辑拖死）。 */
  static final int MAX_CAUSE_DEPTH = 8;

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
                        + "不要输出任何解释性自由文本；rejectReasons 只取上述枚举码，禁止臆造。"
                        + "另可给出认知层字段（缺失即视为空数组，禁止臆造）："
                        + "facts(array of {code,value,evidenceRef}，从材料抽取的结构化事实)、"
                        + "evidenceRefs(array of string，证据定位)、clauseRefs(array of string，条款引用)、"
                        + "discrepancies(array of string，取值严格限定于 ["
                        + String.join(", ", AiDiscrepancyCodes.ALL)
                        + "]；不在清单内的码一律不得输出)。每个数组最多20项、每项字符串最多200字符。"
                        + "认知层字段不得影响金额、rejectSuggested 或 confidence；"
                        + "白名单内的差异码仅供系统把案件从自动通过拉回补件/复核，不是拒赔结论。"),
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
      // 网关任何异常（不可用 / 红线 / 配额 / 内容安全）→ 统一 fail-closed 降级。
      // 原因码让「超时」与「其他网关故障」在决策轨迹里可区分：前者扩容/重试有用，后者没用。
      throw new AiGatewayUnavailableException(
          "AI 网关评估失败，降级纯规则路径: " + ex.getMessage(), ex, classify(ex));
    }
    // 模型未遵循结构化输出契约 → 视为不可信，fail-closed 降级
    return parse(response);
  }

  private AiAssessment parse(LlmResponse response) {
    try {
      JsonNode node = objectMapper.readTree(response.content());
      // G0-2：替身响应显式拒绝。未配 provider 时网关回落 MockLlmClient（自报 mock:true）；
      // 若继续解析会得到「rejectSuggested=false + confidence=null → 转人工」的退化结论，
      // 且 model_version 被伪报成替身模型。显式失败才能给出可运维的修法指引（Rules of ML #10）。
      if (node.path("mock").asBoolean(false)) {
        throw new AiGatewayUnavailableException(
            "检测到 Mock 替身响应，禁止用于自动赔付结论：请配置 bone.ai.gateway.base-url 接入真实 provider",
            AiDegradationReason.MOCK_NOT_ALLOWED);
      }
      boolean reject = node.path("rejectSuggested").asBoolean(false);
      // 置信度 fail-closed（B9）：缺失 / 显式 null / 越界（<0 或 >1）一律视为"无法判定" → null。
      // null 经 DecisionTriage.CONFIDENCE_UNKNOWN 强制转人工，绝不因缺省 0.5 滑入自动通过或复核档。
      JsonNode confNode = node.path("confidence");
      BigDecimal confidence = null;
      if (!confNode.isMissingNode() && !confNode.isNull()) {
        confidence = BigDecimal.valueOf(confNode.asDouble());
        if (confidence.compareTo(BigDecimal.ZERO) < 0 || confidence.compareTo(BigDecimal.ONE) > 0) {
          confidence = null;
        }
      }
      // 仅当建议拒赔时解析原因码，并经白名单归一化（未知码丢弃、空则兜底 OTHER）—— 保证"拒赔必有可解释原因"
      List<String> reasons =
          reject ? normalizeRejectReasons(node.path("rejectReasons")) : List.of();
      // v1.2 认知层：缺失字段 fail-soft 为空列表，绝不因"认知字段没给全"把一次可用评估判成不可用
      return new AiAssessment(
          true,
          reject,
          confidence,
          reasons,
          resolvedModel(response),
          PROMPT_VERSION,
          parseFacts(node.path("facts")),
          parseBoundedTexts(node.path("evidenceRefs"), null),
          parseBoundedTexts(node.path("clauseRefs"), null),
          parseBoundedTexts(node.path("discrepancies"), DISCREPANCY_CODES));
    } catch (AiGatewayUnavailableException ex) {
      // 替身拒绝属于显式降级：保留可运维的原始消息，不要被「解析失败」二次包装掉
      throw ex;
    } catch (Exception ex) {
      throw new AiGatewayUnavailableException(
          "AI 结构化结论解析失败，降级纯规则路径: " + ex.getMessage(),
          ex,
          AiDegradationReason.OUTPUT_CONTRACT_VIOLATION);
    }
  }

  /**
   * 只按异常链类型分类，<b>不匹配消息文本</b>：文案会随上游版本变化，按字符串分类等于把判断建在措辞上， 上游改一句话就会把「超时」静默归成「其他网关故障」。
   *
   * <p>异常链最多遍历 {@value #MAX_CAUSE_DEPTH} 层：自引用或循环 cause 的异常在真实世界里出现过， 这里不能让分类逻辑自己死循环。
   */
  static AiDegradationReason classify(Throwable ex) {
    Throwable current = ex;
    for (int depth = 0; current != null && depth < MAX_CAUSE_DEPTH; depth++) {
      if (current instanceof SocketTimeoutException
          || current instanceof TimeoutException
          || current instanceof HttpTimeoutException) {
        return AiDegradationReason.PROVIDER_TIMEOUT;
      }
      Throwable next = current.getCause();
      current = next == current ? null : next;
    }
    return AiDegradationReason.GATEWAY_ERROR;
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

  /** 解析结构化事实：只取对象形状，三字段均截断，数量封顶——展示数据不得撑爆决策轨迹。 */
  private List<Fact> parseFacts(JsonNode raw) {
    if (raw == null || !raw.isArray()) {
      return List.of();
    }
    List<Fact> facts = new ArrayList<>();
    for (JsonNode item : raw) {
      if (facts.size() >= MAX_COGNITIVE_ITEMS) {
        break;
      }
      if (!item.isObject()) {
        continue;
      }
      facts.add(
          new Fact(
              bounded(item.path("code").asText("")),
              bounded(item.path("value").asText("")),
              bounded(item.path("evidenceRef").asText(""))));
    }
    return List.copyOf(facts);
  }

  /** 解析受控字符串数组：白名单（null = 只截断）、长度与数量三重收敛。 */
  private List<String> parseBoundedTexts(JsonNode raw, List<String> whitelist) {
    if (raw == null || !raw.isArray()) {
      return List.of();
    }
    List<String> values = new ArrayList<>();
    for (JsonNode item : raw) {
      if (values.size() >= MAX_COGNITIVE_ITEMS) {
        break;
      }
      if (!item.isTextual()) {
        continue;
      }
      String value = bounded(item.asText());
      if (value.isEmpty() || (whitelist != null && !whitelist.contains(value))) {
        continue;
      }
      values.add(value);
    }
    return List.copyOf(values);
  }

  private static String bounded(String text) {
    if (text == null) {
      return "";
    }
    String trimmed = text.trim();
    return trimmed.length() <= MAX_COGNITIVE_TEXT
        ? trimmed
        : trimmed.substring(0, MAX_COGNITIVE_TEXT);
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
