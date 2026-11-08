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
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * 图像件感知适配器（D2，{@code bone.claim.document-parse.image.enabled=true} 时装配）。
 *
 * <p>走 bone-ai-gateway 最小多模态扩展（LlmMessage.imageUrl → OpenAI content-parts），复用 AI 安全三件套：{@link
 * PiiMasker} 出站前遮蔽、{@link SafePromptBuilder} 数据槽位、{@link LlmOutputGuard}
 * 字段白名单/必填校验。产物与文本层同构（ParsedDocument：字段 + 置信度 + 坐标 image@code + 完整性）。
 *
 * <p><b>fail-closed</b>：网关异常/超时 → IMAGE_GATEWAY_UNAVAILABLE；非 JSON/字段越界 → IMAGE_OUTPUT_INVALID；mock
 * 自报 → IMAGE_MOCK_REJECTED；无 imageRef → IMAGE_REF_MISSING。 一律 available=false，由应用服务落 FAILED
 * 转人工补录——<b>禁止文本层兜底冒充图像解析成功</b>。
 */
@Component
@ConditionalOnProperty(name = "bone.claim.document-parse.image.enabled", havingValue = "true")
public class ImageDocumentParsePortAdapter implements DocumentParsePort {

  public static final String MODEL = "synap-claim-doc-image-extract";
  public static final String SCENARIO = "claim-doc-image-extract";

  public static final String REASON_OK = "IMAGE_PARSE_OK";
  public static final String REASON_GATEWAY_UNAVAILABLE = "IMAGE_GATEWAY_UNAVAILABLE";
  public static final String REASON_OUTPUT_INVALID = "IMAGE_OUTPUT_INVALID";
  public static final String REASON_MOCK_REJECTED = "IMAGE_MOCK_REJECTED";
  public static final String REASON_REF_MISSING = "IMAGE_REF_MISSING";

  private static final BigDecimal ZERO = BigDecimal.ZERO;

  private final LlmGatewayApplicationService gateway;
  private final ObjectMapper mapper = new ObjectMapper();
  private final DocumentParsePort baseline = new TextLayerDocumentParsePortAdapter();

  public ImageDocumentParsePortAdapter(LlmGatewayApplicationService gateway) {
    this.gateway = gateway;
  }

  @Override
  public ParsedDocument parseImage(DocType docType, String imageRef) {
    if (imageRef == null || imageRef.isBlank()) {
      return unavailable(REASON_REF_MISSING);
    }
    DocType effective = docType == null ? DocType.INVOICE : docType;
    MaskedText masked = PiiMasker.mask("图像件解析：" + effective);
    LlmRequest request =
        new LlmRequest(
            MODEL,
            List.of(
                LlmMessage.system(
                    "你是保险理赔单证结构化抽取助手。用户消息附带一张单证图片，"
                        + "严格输出 JSON 对象："
                        + "{\"fields\":[{\"code\":字符串,\"value\":字符串,\"confidence\":0~1数字}]}。"
                        + "code 只允许 "
                        + LlmDocumentParsePortAdapter.ALLOWED_FIELDS.stream()
                            .sorted()
                            .collect(Collectors.joining(", "))
                        + "；看不清的字段输出空字符串，禁止臆造，禁止输出解释文字。"),
                LlmMessage.userImage(SafePromptBuilder.build(masked.masked(), null), imageRef)),
            null,
            null,
            true,
            false,
            "img-" + UUID.randomUUID(),
            SCENARIO);
    LlmResponse response;
    try {
      response = gateway.invoke(request);
    } catch (RuntimeException ex) {
      return unavailable(REASON_GATEWAY_UNAVAILABLE);
    }
    return parseResponse(effective, response);
  }

  /** 文本层 parse 与图像通道无关：委托确定性基线，行为与既有文本层实现一致。 */
  @Override
  public ParsedDocument parse(DocType docType, String rawText) {
    return baseline.parse(docType, rawText);
  }

  private ParsedDocument parseResponse(DocType docType, LlmResponse response) {
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
      if (code.isBlank()) {
        continue;
      }
      modelValues.put(code, value);
      double raw = field.path("confidence").asDouble(0.0);
      confidences.put(code, BigDecimal.valueOf(Math.max(0.0, Math.min(1.0, raw))));
    }
    Map<String, Object> filtered =
        LlmOutputGuard.filterWhitelist(modelValues, LlmDocumentParsePortAdapter.ALLOWED_FIELDS);
    if (filtered.isEmpty()) {
      return unavailable(REASON_OUTPUT_INVALID);
    }
    List<String> missing =
        LlmOutputGuard.requireFields(
            filtered, TextLayerDocumentParsePortAdapter.requiredFields(docType));

    List<Field> fields = new ArrayList<>();
    BigDecimal min = null;
    for (Map.Entry<String, Object> entry : filtered.entrySet()) {
      BigDecimal confidence = confidences.getOrDefault(entry.getKey(), ZERO);
      if (min == null || confidence.compareTo(min) < 0) {
        min = confidence;
      }
      fields.add(
          new Field(
              entry.getKey(),
              String.valueOf(entry.getValue()),
              confidence,
              "image@" + entry.getKey()));
    }
    if (min == null) {
      min = ZERO;
    }
    boolean complete = missing.isEmpty();
    Map<String, Object> payload = new LinkedHashMap<>();
    payload.put("fields", fields);
    payload.put("mode", "image");
    payload.put("complete", complete);
    payload.put("missing", missing.stream().sorted().toList());
    payload.put("reason", REASON_OK);
    return new ParsedDocument(true, complete, min, fields, toJson(payload), REASON_OK);
  }

  private ParsedDocument unavailable(String reason) {
    Map<String, Object> payload = new LinkedHashMap<>();
    payload.put("fields", List.of());
    payload.put("mode", "image");
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
