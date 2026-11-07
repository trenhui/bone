package com.bone.aidoc.infrastructure.ocr;

import com.bone.aidoc.domain.gateway.OcrQualityEvaluator;
import com.bone.aidoc.domain.model.OcrDocType;
import com.bone.aidoc.domain.model.OcrFieldEvidence;
import com.bone.aidoc.domain.model.QualityReport;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Jev 类质量评估模型的 HTTP 适配器（ADR-0046 L3 真实质量分的接入点）。
 *
 * <p><b>fail-closed</b>：超时 / 非 2xx / 非法 JSON / 分值越界 / 服务未返回该字段，一律让该字段质量分 不可用（{@link
 * QualityReport#none}，{@code scoreFor} 返回 NaN）。此时 CRITICAL 字段强制转二次识别， <b>绝不用启发式分数伪装成模型判断</b>——AUROC
 * 高不等于字段正确，更不等于服务在线。
 *
 * <p>响应契约：{@code {"scores": {"total_amount": 0.97, ...}}}。只采信 [0,1] 内、且属于本批次字段的数值。
 * 本适配器只打分，<b>不改写字段原值</b>。
 */
public class JevHttpQualityEvaluator implements OcrQualityEvaluator {

  private static final ObjectMapper MAPPER = new ObjectMapper();

  private final String endpoint;
  private final HttpOcrClient.OcrTransport transport;

  public JevHttpQualityEvaluator(String baseUrl, HttpOcrClient.OcrTransport transport) {
    this.endpoint = trimTrailingSlash(baseUrl) + "/score";
    this.transport = transport;
  }

  @Override
  public String evaluatorId() {
    return "jev-http";
  }

  @Override
  public QualityReport evaluate(
      List<OcrFieldEvidence> fields, OcrDocType docType, String sourceRef) {
    if (fields == null || fields.isEmpty()) {
      return QualityReport.none(evaluatorId());
    }
    try {
      String body = buildRequestBody(fields, docType, sourceRef);
      String json = transport.post(endpoint, body);
      return parseScores(json, fields);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      return QualityReport.none(evaluatorId());
    } catch (IOException | RuntimeException e) {
      return QualityReport.none(evaluatorId());
    }
  }

  private String buildRequestBody(
      List<OcrFieldEvidence> fields, OcrDocType docType, String sourceRef) throws IOException {
    List<Map<String, String>> payload = new java.util.ArrayList<>();
    for (OcrFieldEvidence f : fields) {
      payload.add(Map.of("name", f.fieldName(), "value", f.value() == null ? "" : f.value()));
    }
    Map<String, Object> req = new LinkedHashMap<>();
    req.put("doc_type", docType.name());
    req.put("source_ref", sourceRef == null ? "" : sourceRef);
    req.put("fields", payload);
    return MAPPER.writeValueAsString(req);
  }

  private QualityReport parseScores(String json, List<OcrFieldEvidence> fields) throws IOException {
    JsonNode root = MAPPER.readTree(json);
    JsonNode scores = root.path("scores");
    Map<String, Double> accepted = new LinkedHashMap<>();
    for (OcrFieldEvidence f : fields) {
      JsonNode node = scores.path(f.fieldName());
      if (!node.isNumber()) {
        continue;
      }
      double v = node.asDouble();
      if (!Double.isNaN(v) && v >= 0.0 && v <= 1.0) {
        accepted.put(f.fieldName(), v);
      }
    }
    if (accepted.isEmpty()) {
      return QualityReport.none(evaluatorId());
    }
    double overall =
        accepted.values().stream().mapToDouble(Double::doubleValue).average().orElse(Double.NaN);
    return new QualityReport(evaluatorId(), accepted, overall);
  }

  private static String trimTrailingSlash(String url) {
    String u = url == null ? "" : url.trim();
    while (u.endsWith("/")) {
      u = u.substring(0, u.length() - 1);
    }
    return u;
  }
}
