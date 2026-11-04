package com.bone.claim.infrastructure.document;

import com.bone.claim.application.port.out.DocumentParsePort;
import com.bone.claim.domain.model.evidence.valueobject.DocType;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

/**
 * 数电票文本层解析适配器（感知层一期实现，零外部依赖）。
 *
 * <p>覆盖 PRD 的一期口径「≥300 条基准集的数电票文本层子集」：读取数电票 PDF 的文本层，按标签抽取票面字段， 每个字段附置信度与原文坐标。图像件/翻拍件没有文本层，返回
 * {@code available=false}，由应用服务 fail-closed 转人工补录，绝不用缺省值冒充解析成功。二期换 bone-ai C1 文档 IR 时只替换本类。
 *
 * <p><b>为什么域内只存文本层结果而不落影像</b>：影像本体走平台附件服务（{@code Evidence.fileRef}）， 本域不持二进制；解析结果（字段 +
 * 坐标）是本域唯一需要的感知产物。
 */
@Component
public class TextLayerDocumentParsePortAdapter implements DocumentParsePort {

  static final String REASON_OK = "OK";
  static final String REASON_TEXT_LAYER_MISSING = "TEXT_LAYER_MISSING";
  static final String REASON_NO_FIELD_EXTRACTED = "NO_FIELD_EXTRACTED";
  static final String REASON_INCOMPLETE_FIELDS = "INCOMPLETE_FIELDS";

  private static final int MIN_TEXT_LENGTH = 16;

  private static final BigDecimal CONFIDENCE_ID = new BigDecimal("0.96");
  private static final BigDecimal CONFIDENCE_AMOUNT = new BigDecimal("0.95");
  private static final BigDecimal CONFIDENCE_DATE = new BigDecimal("0.92");
  private static final BigDecimal CONFIDENCE_CODE = new BigDecimal("0.85");

  private static final List<FieldSpec> SPECS =
      List.of(
          new FieldSpec(
              "invoice_code",
              Pattern.compile("发票代码\\s*[:：]?\\s*([0-9]{10,12})"),
              CONFIDENCE_ID,
              true),
          new FieldSpec(
              "invoice_no", Pattern.compile("发票号码\\s*[:：]?\\s*([0-9]{8,20})"), CONFIDENCE_ID, true),
          new FieldSpec(
              "invoice_date",
              Pattern.compile(
                  "开票日期\\s*[:：]?\\s*(\\d{4}\\s*[-年/.]\\s*\\d{1,2}\\s*[-月/.]\\s*\\d{1,2}\\s*日?)"),
              CONFIDENCE_DATE,
              true),
          new FieldSpec(
              "total_amount",
              Pattern.compile("(?:价税合计|合计金额|合计|总金额)[^0-9]{0,12}([0-9][0-9,]*\\.[0-9]{2})"),
              CONFIDENCE_AMOUNT,
              true),
          new FieldSpec(
              "hospital_code",
              Pattern.compile("(?:医疗机构编码|医院编码|机构编码)\\s*[:：]?\\s*([A-Za-z0-9]{4,20})"),
              CONFIDENCE_CODE,
              false),
          // 关键字段：至少抽到一个才认定「本次解析产出了可核赔事实」；
          // 是否齐备另由 REQUIRED 按单证类型判定（诊断编码对病历是必填，对发票是补充事实）。
          new FieldSpec(
              "diagnosis_code",
              Pattern.compile("(?:诊断编码|诊断|疾病编码)\\s*[:：]?\\s*([A-Z][0-9]{2}(?:\\.[0-9A-Z]{1,4})?)"),
              CONFIDENCE_CODE,
              true));

  /** 各单证类型的必填字段（缺任一即 {@code complete=false}，不允许默写）。 */
  /** 必填字段口径（供模型驱动感知适配器复用，避免两处漂移）。 */
  public static Set<String> requiredFields(DocType docType) {
    return REQUIRED.getOrDefault(docType == null ? DocType.INVOICE : docType, Set.of());
  }

  private static final Map<DocType, Set<String>> REQUIRED =
      Map.of(
          DocType.INVOICE,
          Set.of("invoice_code", "invoice_no", "invoice_date", "total_amount"),
          DocType.LIST,
          Set.of("total_amount"),
          DocType.MEDICAL_RECORD,
          Set.of("diagnosis_code"),
          DocType.MEDICAL_SETTLEMENT,
          Set.of("total_amount", "diagnosis_code"));

  private final ObjectMapper objectMapper = new ObjectMapper();

  @Override
  public ParsedDocument parse(DocType docType, String rawText) {
    DocType effectiveType = docType == null ? DocType.INVOICE : docType;
    if (rawText == null || rawText.trim().length() < MIN_TEXT_LENGTH) {
      return document(
          false, false, BigDecimal.ZERO, List.of(), effectiveType, REASON_TEXT_LAYER_MISSING);
    }

    List<Field> fields = new ArrayList<>();
    boolean anyKeyField = false;
    for (FieldSpec spec : SPECS) {
      Matcher matcher = spec.pattern().matcher(rawText);
      if (!matcher.find()) {
        continue;
      }
      String value = normalize(spec.code(), matcher.group(1));
      fields.add(
          new Field(
              spec.code(),
              value,
              spec.confidence(),
              "text@" + matcher.start(1) + ":" + matcher.group(1).length()));
      anyKeyField |= spec.required();
    }

    // 只抽到可选字段（如机构编码）不算有效解析：票面关键事实缺失时，下游拿不到可核赔的输入。
    if (!anyKeyField) {
      return document(
          true, false, BigDecimal.ZERO, fields, effectiveType, REASON_NO_FIELD_EXTRACTED);
    }
    BigDecimal minConfidence =
        fields.stream().map(Field::confidence).min(BigDecimal::compareTo).orElse(BigDecimal.ZERO);
    Set<String> required = REQUIRED.getOrDefault(effectiveType, Set.of());
    boolean complete =
        fields.stream()
            .map(Field::code)
            .collect(java.util.stream.Collectors.toSet())
            .containsAll(required);
    return document(
        true,
        complete,
        minConfidence,
        fields,
        effectiveType,
        complete ? REASON_OK : REASON_INCOMPLETE_FIELDS);
  }

  private ParsedDocument document(
      boolean available,
      boolean complete,
      BigDecimal minConfidence,
      List<Field> fields,
      DocType docType,
      String reason) {
    Map<String, Object> json = new LinkedHashMap<>();
    json.put("docType", docType.name());
    json.put("available", available);
    json.put("complete", complete);
    json.put("minConfidence", minConfidence);
    json.put("reason", reason);
    json.put("fields", fields);
    String extractedJson;
    try {
      extractedJson = objectMapper.writeValueAsString(json);
    } catch (JsonProcessingException ex) {
      throw new IllegalStateException("解析结果序列化失败", ex);
    }
    return new ParsedDocument(available, complete, minConfidence, fields, extractedJson, reason);
  }

  /** 值归一：金额去千分位、日期统一 ISO；原文原貌由 {@code evidenceRef} 定位，不丢证据。 */
  private static String normalize(String code, String raw) {
    String value = raw == null ? "" : raw.trim();
    if ("total_amount".equals(code)) {
      return value.replace(",", "").replace("￥", "").replace("¥", "");
    }
    if ("invoice_date".equals(code)) {
      Matcher m =
          Pattern.compile("(\\d{4})\\s*[-年/.]\\s*(\\d{1,2})\\s*[-月/.]\\s*(\\d{1,2})")
              .matcher(value);
      if (m.find()) {
        return String.format(
            "%s-%02d-%02d", m.group(1), Integer.parseInt(m.group(2)), Integer.parseInt(m.group(3)));
      }
    }
    return value;
  }

  private record FieldSpec(String code, Pattern pattern, BigDecimal confidence, boolean required) {}
}
