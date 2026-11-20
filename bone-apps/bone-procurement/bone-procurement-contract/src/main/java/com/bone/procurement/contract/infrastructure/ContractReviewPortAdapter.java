package com.bone.procurement.contract.infrastructure;

import com.bone.ai.gateway.application.LlmGatewayApplicationService;
import com.bone.ai.gateway.domain.LlmMessage;
import com.bone.ai.gateway.domain.LlmRequest;
import com.bone.ai.gateway.domain.LlmResponse;
import com.bone.ai.gateway.infrastructure.security.HeuristicInjectionGuard;
import com.bone.ai.gateway.infrastructure.security.JsonOutputValidator;
import com.bone.procurement.contract.application.ConditionalOnContractAiReviewEnabled;
import com.bone.procurement.contract.domain.gateway.ContractReviewPort;
import com.bone.procurement.contract.domain.gateway.ReviewTracePort;
import com.bone.procurement.contract.domain.model.review.AiParticipation;
import com.bone.procurement.contract.domain.model.review.ParsedClause;
import com.bone.procurement.contract.domain.model.review.ReviewRequest;
import com.bone.procurement.contract.domain.model.review.ReviewSuggestion;
import com.bone.procurement.contract.domain.model.review.ReviewTraceEntry;
import com.bone.procurement.contract.domain.model.review.RiskItem;
import com.bone.procurement.contract.domain.model.review.RiskLevel;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * 合同审查出站适配器（生产）：委托 {@link LlmGatewayApplicationService} 调用 LLM。
 *
 * <p>合规护栏：对 user 消息做提示注入检测、对模型输出做 PII 扫描与结构化校验；<b>ai_available 由本节点自报</b>——只有网关成功返回且通过校验才为 {@link
 * AiParticipation#AVAILABLE}；任何异常/降级一律 {@link AiParticipation#UNKNOWN}（fail-closed，绝不静默变
 * UNAVAILABLE）。
 *
 * <p><b>不回传 output 原文</b>：只把模型输出解析为结构化建议，原始 content 不进入领域对象、事件或响应。
 */
@Component
@ConditionalOnContractAiReviewEnabled
public class ContractReviewPortAdapter implements ContractReviewPort {

  private static final Logger log = LoggerFactory.getLogger(ContractReviewPortAdapter.class);
  private static final int MODEL_VERSION_MAX = 128;
  private static final ObjectMapper MAPPER = new ObjectMapper();

  // PII 入口扫描：身份证 18 位（末位可为 X/x）+ 银行账号 16-19 位长数字串。
  // 命中即在入域前拒绝，原文不进领域对象 / 事件 / 报告（D3-v2 §3）。
  private static final Pattern ID_CARD = Pattern.compile("\\d{17}[\\dXx]");
  private static final Pattern BANK_ACCOUNT = Pattern.compile("\\d{16,19}");

  private static final String SYSTEM_PROMPT =
      "你是合同审查辅助助手，只输出 JSON，不要输出解释文字。"
          + "JSON 字段：riskLevel(LOW/MEDIUM/HIGH/UNKNOWN)、confidence(0-1)、"
          + "negativeHit(布尔)、riskItems(数组，每项含 clauseNo/page/spanStart/spanEnd/severity/basis/confidence)。"
          + "你的结论仅为建议，最终决定由人工作出。";

  private final LlmGatewayApplicationService gateway;
  private final ReviewTracePort tracePort;
  private final HeuristicInjectionGuard injectionGuard;
  private final JsonOutputValidator outputValidator;
  private final String model;

  @Autowired
  public ContractReviewPortAdapter(
      LlmGatewayApplicationService gateway,
      ReviewTracePort tracePort,
      @Value("${bone.procurement.contract-ai-review.model:contract-review}") String model) {
    this.gateway = Objects.requireNonNull(gateway, "gateway");
    this.tracePort = Objects.requireNonNull(tracePort, "tracePort");
    // 护栏是合同审查端口的内部职责，不注册为全局网关 bean（避免污染其它 LLM 场景）。
    this.injectionGuard = new HeuristicInjectionGuard();
    this.outputValidator = new JsonOutputValidator();
    this.model = model;
  }

  @Override
  public ReviewSuggestion review(ReviewRequest request) {
    String traceId = request.reviewId();
    try {
      String userMessage = buildUserMessage(request);
      // 入口护栏：命中隐藏指令即抛 BizException，由下方降级为 UNKNOWN 并强制人工。
      injectionGuard.check(userMessage, traceId);
      LlmRequest llmRequest =
          LlmRequest.of(
              model,
              List.of(LlmMessage.system(SYSTEM_PROMPT), LlmMessage.user(userMessage)),
              traceId);
      LlmResponse response = gateway.invoke(llmRequest);
      String content = response.content();
      // PII 入口拒绝：原文（含 basis）在解析成领域对象之前扫描，命中即降级 + 告警，绝不入域。
      if (containsPii(content)) {
        tracePort.append(
            ReviewTraceEntry.of(traceId, "PII_REJECTED", "模型输出命中身份证/银行账号，入口拒绝并降级 UNKNOWN"));
        log.warn("PII_REJECTED: 模型输出含身份证/银行账号，拒绝入域: reviewId={}", traceId);
        return degrade();
      }
      outputValidator.validate(content, traceId);
      return parseSuggestion(content, truncate(response.model()));
    } catch (RuntimeException e) {
      log.warn("合同 AI 审查降级为 UNKNOWN（不产生自动结论）: reviewId={}, reason={}", traceId, e.getMessage());
      return degrade();
    }
  }

  /** 仅把条款锚点 + 正文交给模型，不携带合同全文；结构化的建议由网关层负责出站脱敏。 */
  private static String buildUserMessage(ReviewRequest request) {
    StringBuilder sb = new StringBuilder("请审查以下合同条款：\n");
    for (ParsedClause clause : request.clauses()) {
      sb.append('[')
          .append(clause.clauseNo() == null ? "未编号" : clause.clauseNo())
          .append(" p")
          .append(clause.page())
          .append("] ")
          .append(clause.text())
          .append('\n');
    }
    return sb.toString();
  }

  /** 模型输出入域前的 PII 扫描（含 riskItems.basis 所在的整段原文）。 */
  private static boolean containsPii(String content) {
    return content != null
        && (ID_CARD.matcher(content).find() || BANK_ACCOUNT.matcher(content).find());
  }

  /** 网关不可用 / 护栏命中 / PII 命中 / 输出不合规的统一 fail-closed 结论：UNKNOWN + 低置信 + 无明细。 */
  private static ReviewSuggestion degrade() {
    return new ReviewSuggestion(
        AiParticipation.UNKNOWN, RiskLevel.UNKNOWN, List.of(), 0.0, false, null);
  }

  private static ReviewSuggestion parseSuggestion(String content, String modelVersion) {
    try {
      JsonNode root = MAPPER.readTree(content);
      RiskLevel riskLevel = parseRiskLevel(root.path("riskLevel").asText(null));
      double confidence = root.path("confidence").asDouble(0.0);
      boolean negativeHit = root.path("negativeHit").asBoolean(false);
      List<RiskItem> riskItems = parseRiskItems(root.path("riskItems"));
      return new ReviewSuggestion(
          AiParticipation.AVAILABLE, riskLevel, riskItems, confidence, negativeHit, modelVersion);
    } catch (Exception e) {
      throw new IllegalStateException("审查输出解析失败", e);
    }
  }

  private static RiskLevel parseRiskLevel(String value) {
    if (value == null) {
      return RiskLevel.UNKNOWN;
    }
    try {
      return RiskLevel.valueOf(value.trim().toUpperCase(java.util.Locale.ROOT));
    } catch (IllegalArgumentException e) {
      return RiskLevel.UNKNOWN;
    }
  }

  private static List<RiskItem> parseRiskItems(JsonNode array) {
    List<RiskItem> items = new ArrayList<>();
    if (array == null || !array.isArray()) {
      return items;
    }
    for (JsonNode node : array) {
      String severity = node.path("severity").asText("UNKNOWN");
      items.add(
          new RiskItem(
              node.path("clauseNo").asText(null),
              node.path("page").asInt(0),
              node.path("spanStart").asInt(0),
              node.path("spanEnd").asInt(0),
              parseRiskLevel(severity),
              node.path("basis").asText(null),
              node.path("confidence").asDouble(0.0)));
    }
    return items;
  }

  private static String truncate(String value) {
    if (value == null) {
      return null;
    }
    return value.length() <= MODEL_VERSION_MAX ? value : value.substring(0, MODEL_VERSION_MAX);
  }
}
