package com.bone.aidoc.domain.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.bone.aidoc.domain.model.ClaimDocumentTemplate;
import com.bone.aidoc.domain.model.OcrDocType;
import com.bone.aidoc.domain.model.RequiredField;
import com.bone.aidoc.domain.model.RiskLevel;
import java.util.List;
import org.junit.jupiter.api.Test;

/** 票种版式契约：别名精确匹配、自由文本票种不误报、字段风险模板优先。 */
class ClaimDocumentTemplateRegistryTest {

  private final ClaimDocumentTemplateRegistry registry = ClaimDocumentTemplateRegistry.builtIn();

  @Test
  void normalize_strips_separators_case_and_unicode_space() {
    assertThat(ClaimDocumentTemplate.normalize("Total_Amount")).isEqualTo("totalamount");
    assertThat(ClaimDocumentTemplate.normalize("total amount")).isEqualTo("totalamount");
    assertThat(ClaimDocumentTemplate.normalize("Ｔｏｔａｌ－Ａｍｏｕｎｔ")).isNotBlank();
    assertThat(ClaimDocumentTemplate.normalize(null)).isEmpty();
    assertThat(ClaimDocumentTemplate.normalize("合　计 金-额")).isEqualTo("合计金额");
  }

  @Test
  void invoice_declares_required_fields_and_engine_preference() {
    ClaimDocumentTemplate t = registry.templateFor(OcrDocType.INVOICE);
    assertThat(t.enforcesRequiredFields()).isTrue();
    assertThat(t.requiredFields()).hasSize(3);
    assertThat(registry.preferredEngineOrder(OcrDocType.INVOICE).get(0)).isEqualTo("paddle-ocr");
    assertThat(registry.preferredEngineOrder(OcrDocType.MEDICAL_RECORD).get(0))
        .isEqualTo("glm-ocr");
  }

  @Test
  void alias_match_is_exact_not_substring() {
    // start_date 不应被当作 invoice_date 命中（子串匹配会制造假阴性/假阳性）
    List<RequiredField> missing =
        registry.missingRequiredFields(
            OcrDocType.INVOICE, List.of("total_amount", "invoice_no", "start_date"));
    assertThat(missing).extracting(RequiredField::canonicalName).containsExactly("invoice_date");
  }

  @Test
  void chinese_and_normalized_aliases_satisfy_required_fields() {
    List<RequiredField> missing =
        registry.missingRequiredFields(OcrDocType.INVOICE, List.of("合计金额", "发票号码", "开票日期"));
    assertThat(missing).isEmpty();

    List<RequiredField> missing2 =
        registry.missingRequiredFields(
            OcrDocType.INVOICE, List.of("Total_Amount", "INVOICE-NO", "Date"));
    assertThat(missing2).isEmpty();
  }

  @Test
  void free_text_and_general_doc_types_never_enforce_required_fields() {
    for (OcrDocType type :
        List.of(
            OcrDocType.MEDICAL_RECORD,
            OcrDocType.DISCHARGE_SUMMARY,
            OcrDocType.PRESCRIPTION,
            OcrDocType.GENERAL)) {
      assertThat(registry.missingRequiredFields(type, List.of()))
          .as("票种 %s 不应凭空声明必填字段", type)
          .isEmpty();
    }
  }

  @Test
  void declared_field_risk_takes_priority_over_global_heuristic() {
    assertThat(registry.riskOf(OcrDocType.INVOICE, "合计金额")).contains(RiskLevel.CRITICAL);
    // ID_CARD 的 name 模板为 MEDIUM；GENERAL 无声明 → 由全局启发式回退为 LOW
    assertThat(registry.riskOf(OcrDocType.ID_CARD, "name")).contains(RiskLevel.MEDIUM);
    assertThat(registry.riskOf(OcrDocType.GENERAL, "name")).isEmpty();

    ClaimFieldRisk withTemplate = new ClaimFieldRisk(registry);
    ClaimFieldRisk withoutTemplate =
        new ClaimFieldRisk(ClaimDocumentTemplateRegistry.noTemplates());
    assertThat(withTemplate.levelOf(OcrDocType.ID_CARD, "name")).isEqualTo(RiskLevel.MEDIUM);
    assertThat(withoutTemplate.levelOf(OcrDocType.ID_CARD, "name")).isEqualTo(RiskLevel.LOW);
    assertThat(withoutTemplate.levelOf(OcrDocType.INVOICE, "total_amount"))
        .isEqualTo(RiskLevel.CRITICAL);
  }

  @Test
  void no_templates_registry_disables_all_contracts() {
    ClaimDocumentTemplateRegistry none = ClaimDocumentTemplateRegistry.noTemplates();
    assertThat(none.missingRequiredFields(OcrDocType.INVOICE, List.of())).isEmpty();
    assertThat(none.preferredEngineOrder(OcrDocType.INVOICE)).isEmpty();
    assertThat(none.riskOf(OcrDocType.INVOICE, "total_amount")).isEmpty();
  }
}
