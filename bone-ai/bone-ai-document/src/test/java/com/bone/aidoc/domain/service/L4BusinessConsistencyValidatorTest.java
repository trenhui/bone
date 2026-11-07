package com.bone.aidoc.domain.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.bone.aidoc.domain.model.ConfidenceSource;
import com.bone.aidoc.domain.model.OcrDocType;
import com.bone.aidoc.domain.model.OcrFieldEvidence;
import com.bone.aidoc.domain.model.OcrLineItem;
import com.bone.aidoc.domain.model.OcrResult;
import com.bone.aidoc.domain.model.ReviewStatus;
import com.bone.aidoc.domain.model.ValidationStatus;
import java.util.List;
import org.junit.jupiter.api.Test;

/** L4（费用明细汇总 vs 总额）：确定性拦截 + 误报控制（不适用时绝不制造假红）。 */
class L4BusinessConsistencyValidatorTest {

  private final L4BusinessConsistencyValidator validator =
      new L4BusinessConsistencyValidator(ClaimDocumentTemplateRegistry.builtIn());

  private static OcrFieldEvidence amount(String name, String value) {
    return new OcrFieldEvidence(
        name,
        value,
        null,
        1,
        null,
        "eng",
        0.99,
        ConfidenceSource.OCR_ENGINE,
        ValidationStatus.PASSED,
        ReviewStatus.PENDING);
  }

  private static OcrResult result(OcrLineItem... items) {
    return new OcrResult("stub", OcrDocType.INVOICE, List.of(), List.of(items), "", 1, 0.99);
  }

  private static OcrLineItem item(double value) {
    return new OcrLineItem("检查费", value);
  }

  private static ValidationStatus statusOf(L4BusinessConsistencyValidator.Outcome o) {
    return o.fields().get(0).validationStatus();
  }

  @Test
  void consistent_sum_keeps_status_and_records_reason() {
    var o =
        validator.validate(
            result(item(30.0), item(70.0)),
            List.of(amount("total_amount", "100.00")),
            OcrDocType.INVOICE);
    assertThat(statusOf(o)).isEqualTo(ValidationStatus.PASSED);
    assertThat(o.reasons().get("total_amount")).contains("一致");
  }

  @Test
  void inconsistent_sum_downgrades_total_to_failed() {
    var o =
        validator.validate(
            result(item(30.0), item(70.0)),
            List.of(amount("total_amount", "200.00")),
            OcrDocType.INVOICE);
    assertThat(statusOf(o)).isEqualTo(ValidationStatus.FAILED);
    assertThat(o.reasons().get("total_amount")).contains("不一致", "100.00", "200.00");
    assertThat(o.fields().get(0).fieldName()).isEqualTo("total_amount");
  }

  @Test
  void chinese_alias_is_resolved_from_the_single_template_source() {
    var o =
        validator.validate(
            result(item(100.0)), List.of(amount("合计金额", "50.00")), OcrDocType.INVOICE);
    assertThat(statusOf(o)).isEqualTo(ValidationStatus.FAILED);
    assertThat(o.reasons()).containsKey("合计金额");
  }

  @Test
  void one_cent_tolerance_is_accepted() {
    var o =
        validator.validate(
            result(item(100.00)), List.of(amount("total_amount", "100.01")), OcrDocType.INVOICE);
    assertThat(statusOf(o)).isEqualTo(ValidationStatus.PASSED);
  }

  @Test
  void no_line_items_is_a_no_op() {
    List<OcrFieldEvidence> fields = List.of(amount("total_amount", "100.00"));
    var o = validator.validate(result(), fields, OcrDocType.INVOICE);
    assertThat(o.fields()).isEqualTo(fields);
    assertThat(o.reasons()).isEmpty();
  }

  @Test
  void two_total_fields_are_not_guessed() {
    List<OcrFieldEvidence> fields =
        List.of(amount("total_amount", "100.00"), amount("合计金额", "100.00"));
    var o = validator.validate(result(item(30.0), item(70.0)), fields, OcrDocType.INVOICE);
    assertThat(o.fields()).isEqualTo(fields);
    assertThat(o.reasons().get("[L4]")).contains("未唯一确定");
  }

  @Test
  void unparsable_total_is_a_no_op() {
    List<OcrFieldEvidence> fields = List.of(amount("total_amount", "壹佰元"));
    var o = validator.validate(result(item(100.0)), fields, OcrDocType.INVOICE);
    assertThat(statusOf(o)).isEqualTo(ValidationStatus.PASSED);
    assertThat(o.reasons().get("[L4]")).contains("不可解析");
  }

  @Test
  void illegal_line_item_amount_is_a_no_op() {
    List<OcrFieldEvidence> fields = List.of(amount("total_amount", "100.00"));
    var o = validator.validate(result(item(-1.0)), fields, OcrDocType.INVOICE);
    assertThat(o.fields()).isEqualTo(fields);
    assertThat(o.reasons().get("[L4]")).contains("非法金额");
  }

  @Test
  void no_template_declaration_is_a_no_op() {
    L4BusinessConsistencyValidator none =
        new L4BusinessConsistencyValidator(ClaimDocumentTemplateRegistry.noTemplates());
    List<OcrFieldEvidence> fields = List.of(amount("total_amount", "100.00"));
    var o = none.validate(result(item(1.0)), fields, OcrDocType.INVOICE);
    assertThat(o.fields()).isEqualTo(fields);
  }

  @Test
  void unrelated_field_name_is_not_treated_as_total() {
    List<OcrFieldEvidence> fields = List.of(amount("invoice_date", "100.00"));
    var o = validator.validate(result(item(1.0)), fields, OcrDocType.INVOICE);
    assertThat(o.fields()).isEqualTo(fields);
  }
}
