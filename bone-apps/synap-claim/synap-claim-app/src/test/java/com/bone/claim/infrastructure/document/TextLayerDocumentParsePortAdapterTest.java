package com.bone.claim.infrastructure.document;

import static org.assertj.core.api.Assertions.assertThat;

import com.bone.claim.application.port.out.DocumentParsePort;
import com.bone.claim.domain.model.evidence.valueobject.DocType;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** 感知层解析契约：字段、置信度、原文坐标、完整性与 fail-closed 四类判据都要可证伪。 */
class TextLayerDocumentParsePortAdapterTest {

  private final TextLayerDocumentParsePortAdapter adapter = new TextLayerDocumentParsePortAdapter();

  private static final String FULL_INVOICE =
      "电子发票（普通发票）\n"
          + "发票代码：0440000123\n"
          + "发票号码：12345678\n"
          + "开票日期：2026年3月12日\n"
          + "医疗机构编码：H44010001\n"
          + "诊断编码：J06.9\n"
          + "价税合计（小写）：¥1,234.56\n"
          + "购买方名称：某某\n";

  @Test
  @DisplayName("完整数电票：产出字段级置信度与原文坐标，且必填字段齐备")
  void fullInvoiceProducesFieldsWithConfidenceAndCoordinates() throws Exception {
    DocumentParsePort.ParsedDocument doc = adapter.parse(DocType.INVOICE, FULL_INVOICE);

    assertThat(doc.available()).isTrue();
    assertThat(doc.complete()).isTrue();
    assertThat(doc.reason()).isEqualTo(TextLayerDocumentParsePortAdapter.REASON_OK);
    assertThat(doc.fields())
        .extracting(DocumentParsePort.Field::code)
        .contains(
            "invoice_code",
            "invoice_no",
            "invoice_date",
            "total_amount",
            "diagnosis_code",
            "hospital_code");
    assertThat(doc.minConfidence()).isEqualByComparingTo(new BigDecimal("0.85"));
    assertThat(doc.fields())
        .allSatisfy(
            f -> {
              assertThat(f.confidence()).isGreaterThanOrEqualTo(new BigDecimal("0.7"));
              assertThat(f.evidenceRef()).matches("text@\\d+:\\d+");
              String[] coordinate = f.evidenceRef().split("@")[1].split(":");
              String original =
                  FULL_INVOICE.substring(
                      Integer.parseInt(coordinate[0]),
                      Integer.parseInt(coordinate[0]) + Integer.parseInt(coordinate[1]));
              assertThat(original).isNotBlank();
            });

    JsonNode json = new ObjectMapper().readTree(doc.extractedJson());
    assertThat(json.path("fields").isArray()).isTrue();
    assertThat(json.path("fields").size()).isEqualTo(doc.fields().size());
    assertThat(json.path("fields").get(0).path("evidenceRef").asText()).startsWith("text@");
  }

  @Test
  @DisplayName("值归一：中文日期转 ISO、金额去千分位，原文由坐标保留")
  void normalizesValuesWithoutLosingEvidence() {
    DocumentParsePort.ParsedDocument doc = adapter.parse(DocType.INVOICE, FULL_INVOICE);

    assertThat(field(doc, "invoice_date").value()).isEqualTo("2026-03-12");
    assertThat(field(doc, "total_amount").value()).isEqualTo("1234.56");
    int offset =
        Integer.parseInt(field(doc, "total_amount").evidenceRef().split("@")[1].split(":")[0]);
    assertThat(FULL_INVOICE.substring(offset, offset + 8)).isEqualTo("1,234.56");
  }

  @Test
  @DisplayName("缺关键字段：complete=false 且落受控原因码，绝不默写")
  void missingKeyFieldIsIncomplete() {
    String withoutInvoiceNo = FULL_INVOICE.replace("发票号码：12345678\n", "");

    DocumentParsePort.ParsedDocument doc = adapter.parse(DocType.INVOICE, withoutInvoiceNo);

    assertThat(doc.available()).isTrue();
    assertThat(doc.complete()).isFalse();
    assertThat(doc.reason()).isEqualTo(TextLayerDocumentParsePortAdapter.REASON_INCOMPLETE_FIELDS);
    assertThat(doc.fields()).extracting(DocumentParsePort.Field::code).doesNotContain("invoice_no");
  }

  @Test
  @DisplayName("无文本层：available=false，消费方必须转人工")
  void missingTextLayerFailsClosed() throws Exception {
    DocumentParsePort.ParsedDocument doc = adapter.parse(DocType.INVOICE, "  ");

    assertThat(doc.available()).isFalse();
    assertThat(doc.complete()).isFalse();
    assertThat(doc.fields()).isEmpty();
    assertThat(doc.reason()).isEqualTo(TextLayerDocumentParsePortAdapter.REASON_TEXT_LAYER_MISSING);
    assertThat(new ObjectMapper().readTree(doc.extractedJson()).path("available").asBoolean())
        .isFalse();
  }

  @Test
  @DisplayName("只抽到可选字段：不算有效解析，避免拿机构编码当票面事实")
  void optionalFieldOnlyIsNotAValidParse() {
    String text = "某某医院就诊凭证，共若干项\n医疗机构编码：H44010001\n其他说明文字\n";

    DocumentParsePort.ParsedDocument doc = adapter.parse(DocType.INVOICE, text);

    assertThat(doc.available()).isTrue();
    assertThat(doc.complete()).isFalse();
    assertThat(doc.reason()).isEqualTo(TextLayerDocumentParsePortAdapter.REASON_NO_FIELD_EXTRACTED);
    assertThat(doc.fields()).hasSize(1);
  }

  @Test
  @DisplayName("病历单证：必填字段按单证类型判定，而非固定发票四要素")
  void requiredFieldsDependOnDocType() {
    String record = "门诊病历记录一份\n诊断编码：J06.9\n就诊日期说明文字若干\n";

    DocumentParsePort.ParsedDocument doc = adapter.parse(DocType.MEDICAL_RECORD, record);

    assertThat(doc.complete()).isTrue();
    assertThat(doc.reason()).isEqualTo(TextLayerDocumentParsePortAdapter.REASON_OK);
  }

  private static DocumentParsePort.Field field(DocumentParsePort.ParsedDocument doc, String code) {
    return doc.fields().stream().filter(f -> f.code().equals(code)).findFirst().orElseThrow();
  }
}
