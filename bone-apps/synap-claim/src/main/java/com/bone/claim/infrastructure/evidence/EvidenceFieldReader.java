package com.bone.claim.infrastructure.evidence;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.List;

/**
 * 材料解析字段读取器（{@code t_claim_evidence.extracted_json} 的唯一解析入口）。
 *
 * <p><b>为什么收敛到一处</b>：知识层与案件上下文层都要读同一份结构化解析结果，此前各有一份 {@code fieldsOf}。两份实现对「坏 JSON / 缺 {@code
 * fields} 键 / 非数组」的处置一旦分叉，就会出现「知识层说有事实、认知层说没材料」的互相矛盾—— 而这类矛盾在排查时最难定位（两处都"看起来对"）。语义固定为：任何不合法输入 ⇒
 * 空列表，绝不抛异常打断决策图。
 */
public final class EvidenceFieldReader {

  private static final ObjectMapper MAPPER = new ObjectMapper();

  private EvidenceFieldReader() {}

  /** 单条解析字段：值可能为空串（调用方自行决定是否跳过）。 */
  public record ParsedField(String code, String value, String evidenceRef) {}

  /** 读取 {@code extracted_json} 的 {@code fields} 数组；不可解析时返回空列表。 */
  public static List<ParsedField> read(String extractedJson) {
    if (extractedJson == null || extractedJson.isBlank()) {
      return List.of();
    }
    JsonNode fields;
    try {
      fields = MAPPER.readTree(extractedJson).path("fields");
    } catch (Exception ex) {
      // 损坏 / 非 JSON 的解析结果一律视为「没有事实」：知识层据此 fail-closed，认知层据此提示词里没有材料。
      return List.of();
    }
    if (!fields.isArray()) {
      return List.of();
    }
    List<ParsedField> parsed = new ArrayList<>(fields.size());
    for (JsonNode field : fields) {
      parsed.add(
          new ParsedField(
              field.path("code").asText(""),
              field.path("value").asText(""),
              field.path("evidenceRef").asText("")));
    }
    return List.copyOf(parsed);
  }
}
