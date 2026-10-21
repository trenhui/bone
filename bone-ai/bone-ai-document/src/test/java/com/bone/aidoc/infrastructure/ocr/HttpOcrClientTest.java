package com.bone.aidoc.infrastructure.ocr;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

import com.bone.aidoc.common.AiDocErrorCodes;
import com.bone.aidoc.domain.model.OcrDocType;
import com.bone.aidoc.domain.model.OcrResult;
import com.bone.aidoc.domain.model.RecognizedField;
import com.bone.core.exception.BizException;
import org.junit.jupiter.api.Test;

class HttpOcrClientTest {

  @Test
  void parses_fields_line_items_and_metadata() {
    String json =
        "{\"fields\":[{\"name\":\"invoice_no\",\"value\":\"FP123\",\"confidence\":0.98},"
            + "{\"name\":\"hospital\",\"value\":\"市一院\",\"confidence\":0.9}],"
            + "\"line_items\":[{\"item_name\":\"检查费\",\"amount\":120.5},"
            + "{\"item_name\":\"药费\",\"amount\":80.0}],"
            + "\"text\":\"发票全文\",\"page_count\":1,\"confidence\":0.95}";

    OcrResult r = HttpOcrClient.parseResult(json, OcrDocType.INVOICE);

    assertThat(r.docType()).isEqualTo(OcrDocType.INVOICE);
    assertThat(r.provider()).isEqualTo("http-ocr");
    assertThat(r.fields())
        .containsExactly(
            new RecognizedField("invoice_no", "FP123", 0.98),
            new RecognizedField("hospital", "市一院", 0.9));
    assertThat(r.lineItems()).hasSize(2);
    assertThat(r.lineItems().get(0).itemName()).isEqualTo("检查费");
    assertThat(r.lineItems().get(0).amount()).isEqualTo(120.5);
    assertThat(r.rawText()).isEqualTo("发票全文");
    assertThat(r.pageCount()).isEqualTo(1);
    assertThat(r.confidence()).isEqualTo(0.95);
  }

  @Test
  void tolerates_missing_sections_as_empty() {
    OcrResult r = HttpOcrClient.parseResult("{}", OcrDocType.MEDICAL_RECORD);

    assertThat(r.fields()).isEmpty();
    assertThat(r.lineItems()).isEmpty();
    assertThat(r.rawText()).isEmpty();
    assertThat(r.confidence()).isZero();
  }

  @Test
  void rejects_malformed_response_with_coded_error() {
    BizException ex =
        catchThrowableOfType(
            () -> HttpOcrClient.parseResult("not-json", OcrDocType.GENERAL), BizException.class);
    assertThat(ex.getErrorCode()).isEqualTo(AiDocErrorCodes.OCR_PROVIDER_FAIL);
  }
}
