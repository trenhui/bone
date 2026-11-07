package com.bone.aidoc.domain.service;

import com.bone.aidoc.domain.model.ClaimDocumentTemplate;
import com.bone.aidoc.domain.model.OcrDocType;
import com.bone.aidoc.domain.model.RequiredField;
import com.bone.aidoc.domain.model.RiskLevel;
import java.util.Collection;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 票种版式契约注册表（纯域查表，唯一事实源）。
 *
 * <p>把「按票种分别选型」与「按票种分别校验」收敛到同一份领域表：引擎优先顺序与必填字段都在这里声明， 避免同一语义散落在配置与代码两处导致「配了不生效」。内置值是<b>骨架默认</b>，须由
 * PoC 实测校准， 不得当作已验证的准确率结论（通用榜单分 ≠ 理赔字段级准确率）。
 *
 * <p>可通过构造注入自定义 Map 整体替换；{@link #noTemplates()} 用于显式关闭模板能力（如单测隔离）。
 */
public class ClaimDocumentTemplateRegistry {

  private static final List<String> STRUCTURED_FIRST =
      List.of("paddle-ocr", "glm-ocr", "minicpm-ocr");
  private static final List<String> VLM_FIRST = List.of("glm-ocr", "minicpm-ocr", "paddle-ocr");

  private final Map<OcrDocType, ClaimDocumentTemplate> templates;

  public ClaimDocumentTemplateRegistry() {
    this(builtInMap());
  }

  public ClaimDocumentTemplateRegistry(Map<OcrDocType, ClaimDocumentTemplate> templates) {
    this.templates = new EnumMap<>(OcrDocType.class);
    if (templates != null) {
      this.templates.putAll(templates);
    }
  }

  /** 内置骨架模板（默认构造等价）。 */
  public static ClaimDocumentTemplateRegistry builtIn() {
    return new ClaimDocumentTemplateRegistry();
  }

  /** 空注册表：不拦截缺字段、不改变引擎顺序（用于隔离测试或显式退化）。 */
  public static ClaimDocumentTemplateRegistry noTemplates() {
    return new ClaimDocumentTemplateRegistry(Map.of());
  }

  /** 未登记票种返回空契约，不臆断其版式。 */
  public ClaimDocumentTemplate templateFor(OcrDocType docType) {
    return templates.getOrDefault(docType, ClaimDocumentTemplate.empty(docType));
  }

  /** 该票种下未被识别出的必填字段（字段名经归一化精确比对别名）。 */
  public List<RequiredField> missingRequiredFields(
      OcrDocType docType, Collection<String> recognizedFieldNames) {
    Set<String> normalized =
        recognizedFieldNames.stream()
            .map(ClaimDocumentTemplate::normalize)
            .collect(Collectors.toSet());
    return templateFor(docType).missingFields(normalized);
  }

  /** 模板声明的字段风险优先；未声明返回空，由 {@link ClaimFieldRisk} 的全局启发式回退。 */
  public Optional<RiskLevel> riskOf(OcrDocType docType, String fieldName) {
    return templateFor(docType).riskOf(ClaimDocumentTemplate.normalize(fieldName));
  }

  /** 该票种偏好的引擎顺序（未列入者按装配顺序排后）。 */
  public List<String> preferredEngineOrder(OcrDocType docType) {
    return templateFor(docType).preferredEngineIds();
  }

  private static Map<OcrDocType, ClaimDocumentTemplate> builtInMap() {
    Map<OcrDocType, ClaimDocumentTemplate> m = new EnumMap<>(OcrDocType.class);

    m.put(
        OcrDocType.INVOICE,
        new ClaimDocumentTemplate(
            OcrDocType.INVOICE,
            List.of(
                RequiredField.of(
                    "total_amount",
                    RiskLevel.CRITICAL,
                    "amount",
                    "invoice_amount",
                    "sum_amount",
                    "total",
                    "合计金额",
                    "总金额",
                    "金额合计",
                    "费用总额",
                    "金额"),
                RequiredField.of(
                    "invoice_no",
                    RiskLevel.HIGH,
                    "invoice_code",
                    "invoice_number",
                    "fapiao_no",
                    "发票号",
                    "发票号码",
                    "票据号",
                    "发票代码"),
                RequiredField.of(
                    "invoice_date", RiskLevel.HIGH, "billing_date", "date", "开票日期", "日期")),
            STRUCTURED_FIRST));

    m.put(
        OcrDocType.FEE_LIST,
        new ClaimDocumentTemplate(
            OcrDocType.FEE_LIST,
            List.of(
                RequiredField.of(
                    "total_amount", RiskLevel.CRITICAL, "amount", "合计金额", "总金额", "费用总额", "金额")),
            STRUCTURED_FIRST));

    m.put(
        OcrDocType.ID_CARD,
        new ClaimDocumentTemplate(
            OcrDocType.ID_CARD,
            List.of(
                RequiredField.of(
                    "id_card_no",
                    RiskLevel.HIGH,
                    "id_no",
                    "idnumber",
                    "cert_no",
                    "身份证号",
                    "身份证号码",
                    "证件号码"),
                RequiredField.of("name", RiskLevel.MEDIUM, "patient_name", "姓名", "身份证姓名")),
            STRUCTURED_FIRST));

    m.put(
        OcrDocType.BANK_CARD,
        new ClaimDocumentTemplate(
            OcrDocType.BANK_CARD,
            List.of(
                RequiredField.of(
                    "bank_card_no",
                    RiskLevel.HIGH,
                    "card_no",
                    "cardno",
                    "account_no",
                    "银行卡号",
                    "卡号")),
            STRUCTURED_FIRST));

    m.put(
        OcrDocType.POLICY,
        new ClaimDocumentTemplate(
            OcrDocType.POLICY,
            List.of(RequiredField.of("policy_no", RiskLevel.HIGH, "policyno", "保单号", "保单号码")),
            VLM_FIRST));

    // 自由文本票种：只声明引擎偏好，不声明必填字段（schema 不稳定，声明即制造假红）
    m.put(
        OcrDocType.MEDICAL_RECORD,
        new ClaimDocumentTemplate(OcrDocType.MEDICAL_RECORD, List.of(), VLM_FIRST));
    m.put(
        OcrDocType.DISCHARGE_SUMMARY,
        new ClaimDocumentTemplate(OcrDocType.DISCHARGE_SUMMARY, List.of(), VLM_FIRST));
    m.put(
        OcrDocType.PRESCRIPTION,
        new ClaimDocumentTemplate(OcrDocType.PRESCRIPTION, List.of(), VLM_FIRST));
    // GENERAL 刻意不登记：templateFor 返回空契约
    return m;
  }
}
