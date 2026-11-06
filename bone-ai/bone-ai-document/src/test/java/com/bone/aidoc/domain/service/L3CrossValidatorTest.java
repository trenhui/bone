package com.bone.aidoc.domain.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.bone.aidoc.domain.model.CrossCheck;
import com.bone.aidoc.domain.model.OcrDocType;
import com.bone.aidoc.domain.model.OcrResult;
import com.bone.aidoc.domain.model.RecognizedField;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** {@link L3CrossValidator} 断言（多模型交叉）。 */
class L3CrossValidatorTest {

  private OcrResult result(List<RecognizedField> fields) {
    return new OcrResult("p", OcrDocType.INVOICE, fields, List.of(), "", 1, 0.9);
  }

  @Test
  void agreement_and_disagreement_and_absent() {
    OcrResult primary =
        result(
            List.of(
                new RecognizedField("total_amount", "100.00", 0.99),
                new RecognizedField("invoice_no", "A123", 0.99),
                new RecognizedField("hospital_name", "某医院", 0.99)));
    OcrResult secondary =
        result(
            List.of(
                new RecognizedField("total_amount", "100.00", 0.98),
                new RecognizedField("invoice_no", "B456", 0.98)));
    Map<String, CrossCheck> cross = L3CrossValidator.compare(primary, secondary);
    assertThat(cross.get("total_amount")).isEqualTo(CrossCheck.MATCH);
    assertThat(cross.get("invoice_no")).isEqualTo(CrossCheck.MISMATCH);
    assertThat(cross.get("hospital_name")).isEqualTo(CrossCheck.NOT_RUN);
  }

  @Test
  void numeric_tolerance_treated_as_match() {
    OcrResult primary = result(List.of(new RecognizedField("total_amount", "100.00", 0.99)));
    OcrResult secondary = result(List.of(new RecognizedField("total_amount", "100.0", 0.98)));
    assertThat(L3CrossValidator.compare(primary, secondary).get("total_amount"))
        .isEqualTo(CrossCheck.MATCH);
  }
}
