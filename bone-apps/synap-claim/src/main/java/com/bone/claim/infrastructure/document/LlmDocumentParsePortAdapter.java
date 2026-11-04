package com.bone.claim.infrastructure.document;

import com.bone.ai.gateway.application.LlmGatewayApplicationService;
import com.bone.ai.gateway.domain.LlmMessage;
import com.bone.ai.gateway.domain.LlmRequest;
import com.bone.ai.gateway.domain.LlmResponse;
import com.bone.claim.application.port.out.DocumentParsePort;
import com.bone.claim.domain.model.evidence.valueobject.DocType;
import com.bone.claim.domain.security.LlmOutputGuard;
import com.bone.claim.domain.security.MaskedText;
import com.bone.claim.domain.security.PiiMasker;
import com.bone.claim.domain.security.SafePromptBuilder;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

/**
 * 模型驱动的感知层适配器（{@code bone.claim.document-parse.mode=llm} 时装配并覆盖文本层实现）。
 *
 * <p><b>为什么需要它</b>：文本层正则解析只能覆盖数电票，且「感知由模型承担」这条 AI 原生判据始终不成立。 本实现让 LLM 成为单证事实抽取的主执行体，产物仍遵守 {@link
 * ParsedDocument} 契约（字段 + 置信度 + 证据定位 + 完整性），因此下游知识层 / 验真 / 决策上下文零改动即可消费模型产物。
 *
 * <p><b>AI 安全三件套在此进入生产链（消费方纵深防御，不是替代网关 C8）</b>： {@link PiiMasker} 出站前遮蔽、{@link SafePromptBuilder}
 * 把材料放进不可执行的数据槽位、 {@link LlmOutputGuard} 做字段白名单 / 必填校验 / 与确定性基线交叉验证。网关仍统一负责 PII
 * 脱敏、注入检测、内容安全、计量与熔断；本类只做「送出去之前」与「拿回来之后」的确定性校验， 不构成第二套安全策略。
 *
 * <p><b>AI 不碰已确定事实</b>：文本层正则解析结果作为确定性基线，与模型输出冲突时以基线为准并降置信度， 冲突字段落在 {@code
 * extracted_json.conflicts}，供核赔员与评测集消费。
 *
 * <p><b>fail-closed</b>：未配真实 provider 时网关回落 {@code MockLlmClient}（自报 {@code mock:true}），
 * 本适配器显式拒绝；网关异常、非 JSON、白名单外空结果一律 {@code available=false} → 应用服务落 {@code FAILED}
 * 转人工补录，绝不把解析失败解释成「已解析且无异常」。
 */
@Primary
@Component
@ConditionalOnProperty(name = "bone.claim.document-parse.mode", havingValue = "llm")
public class LlmDocumentParsePortAdapter implements DocumentParsePort {

  public static final String MODEL = "synap-claim-doc-extract";
  public static final String SCENARIO = "claim-doc-extract";

  public static final Set<String> ALLOWED_FIELDS =
      Set.of(
          "invoice_code",
          "invoice_no",
          "invoice_date",
          "total_amount",
          "hospital_code",
          "diagnosis_code",
          "drug_code");

  public static final String REASON_OK = "LLM_PARSE_OK";
  public static final String REASON_CONFLICT = "LLM_PARSE_CONFLICT";
  public static final String REASON_LLM_UNAVAILABLE = "LLM_UNAVAILABLE";
  public static final String REASON_OUTPUT_INVALID = "LLM_OUTPUT_INVALID";
  public static final String REASON_MOCK_REJECTED = "LLM_MOCK_REJECTED";

  /** 冲突字段以基线值为准，置信度降到该值：不是模型判定不可信，而是「此处不采信模型」。 */
  private static final BigDecimal CONFLICT_CONFIDENCE = new BigDecimal("0.50");

  private static final BigDecimal DEFAULT_CONFIDENCE = new BigDecimal("0.70");
  private static final BigDecimal ZERO = BigDecimal.ZERO;

  private final LlmGatewayApplicationService gateway;
  private final ObjectMapper mapper = new ObjectMapper();
  private final DocumentParsePort baseline = new TextLayerDocumentParsePortAdapter();

  public LlmDocumentParsePortAdapter(LlmGatewayApplicationService gateway) {
    this.gateway = gateway;
  }

  @Override
  public ParsedDocument parse(DocType docType, String rawText) {
    DocType effective = docType == null ? DocType.INVOICE : docType;
    if (rawText == null || rawText.isBlank()) {
      return unavailable(TextLayerDocumentParsePortAdapter.REASON_TEXT_LAYER_MISSING);
    }
    MaskedText masked = PiiMasker.mask(rawText);
    LlmRequest request =
        new LlmRequest(
            MODEL,
            List.of(
                LlmMessage.system(
                    "你是保险理赔单证结构化抽取助手。严格输出 JSON 对象："
                        + "{\"fields\":[{\"code\":字符串,\"value\":字符串,\"confidence\":0~1数字}]}。"
                        + "code 只允许 "
                        + ALLOWED_FIELDS.stream().sorted().collect(Collectors.joining(", "))
                        + "；未知字段不要输出，禁止臆造，禁止输出解释文字。"),
                LlmMessage.user(SafePromptBuilder.build(masked.masked(), null))),
            null,
            null,
            true,
            false,
            "doc-" + UUID.randomUUID(),
            SCENARIO);
    LlmResponse response;
    try {
      response = gateway.invoke(request);
    } catch (RuntimeException ex) {
      return unavailable(REASON_LLM_UNAVAILABLE);
    }
    return parseResponse(effective, rawText, response);
  }

  private ParsedDocument parseResponse(DocType docType, String rawText, LlmResponse response) {
    JsonNode root;
    try {
      root = mapper.readTree(response.content());
    } catch (Exception ex) {
      return unavailable(REASON_OUTPUT_INVALID);
    }
    if (root.path("mock").asBoolean(false)) {
      return unavailable(REASON_MOCK_REJECTED);
    }
    JsonNode array = root.path("fields");
    if (!array.isArray()) {
      return unavailable(REASON_OUTPUT_INVALID);
    }

    Map<String, Object> modelValues = new LinkedHashMap<>();
    Map<String, BigDecimal> confidences = new LinkedHashMap<>();
    for (JsonNode field : array) {
      String code = field.path("code").asText("");
      String value = field.path("value").asText("");
      if (code.isBlank() || value.isBlank()) {
        continue;
      }
      modelValues.put(code, value);
      double raw = field.path("confidence").asDouble(0.0);
      confidences.put(code, BigDecimal.valueOf(Math.max(0.0, Math.min(1.0, raw))));
    }
    Map<String, Object> filtered = LlmOutputGuard.filterWhitelist(modelValues, ALLOWED_FIELDS);
    if (filtered.isEmpty()) {
      return unavailable(REASON_OUTPUT_INVALID);
    }

    // 确定性基线：文本层正则解析。只比对两侧都抽到的字段，模型独有字段保留模型值。
    Map<String, Object> baselineValues = new LinkedHashMap<>();
    for (Field f : baseline.parse(docType, rawText).fields()) {
      baselineValues.put(f.code(), f.value());
    }
    List<String> comparable = new ArrayList<>(baselineValues.keySet());
    comparable.retainAll(filtered.keySet());
    List<LlmOutputGuard.Conflict> conflicts =
        LlmOutputGuard.crossCheck(filtered, baselineValues, comparable);
    Set<String> conflictFields =
        conflicts.stream().map(LlmOutputGuard.Conflict::field).collect(Collectors.toSet());

    List<Field> fields = new ArrayList<>();
    for (Map.Entry<String, Object> entry : filtered.entrySet()) {
      String code = entry.getKey();
      Object baselineValue = baselineValues.get(code);
      boolean conflict = conflictFields.contains(code) && baselineValue != null;
      fields.add(
          new Field(
              code,
              conflict ? String.valueOf(baselineValue) : String.valueOf(entry.getValue()),
              conflict ? CONFLICT_CONFIDENCE : confidences.getOrDefault(code, DEFAULT_CONFIDENCE),
              conflict ? "baseline@" + code : "llm@" + code));
    }
    boolean complete =
        LlmOutputGuard.requireFields(
                filtered, TextLayerDocumentParsePortAdapter.requiredFields(docType))
            .isEmpty();
    BigDecimal minConfidence =
        fields.stream().map(Field::confidence).min(BigDecimal::compareTo).orElse(ZERO);
    String reason = conflicts.isEmpty() ? REASON_OK : REASON_CONFLICT;

    Map<String, Object> payload = new LinkedHashMap<>();
    payload.put("fields", fields);
    payload.put("basis", "LLM_WITH_DETERMINISTIC_BASELINE");
    payload.put("mode", "llm");
    payload.put("conflicts", conflictFields.stream().sorted().toList());
    payload.put("reason", reason);
    return new ParsedDocument(true, complete, minConfidence, fields, toJson(payload), reason);
  }

  private ParsedDocument unavailable(String reason) {
    Map<String, Object> payload = new LinkedHashMap<>();
    payload.put("fields", List.of());
    payload.put("mode", "llm");
    payload.put("available", false);
    payload.put("reason", reason);
    return new ParsedDocument(false, false, ZERO, List.of(), toJson(payload), reason);
  }

  private String toJson(Map<String, Object> payload) {
    try {
      return mapper.writeValueAsString(payload);
    } catch (Exception ex) {
      return "{\"fields\":[],\"reason\":\"SERIALIZE_FAILED\"}";
    }
  }
}
