package com.bone.aidoc.domain.service;

import com.bone.aidoc.domain.model.ClaimDocumentTemplate;
import com.bone.aidoc.domain.model.OcrDocType;
import com.bone.aidoc.domain.model.OcrFieldEvidence;
import com.bone.aidoc.domain.model.OcrLineItem;
import com.bone.aidoc.domain.model.OcrResult;
import com.bone.aidoc.domain.model.RequiredField;
import com.bone.aidoc.domain.model.ValidationStatus;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * L4 业务逻辑校验：费用明细汇总与总额一致性（ADR-0046 §五 L4）。
 *
 * <p>此前 {@code OcrResult.lineItems} 与 {@link OcrLineItem} 已定义却无任何消费方， 五层可信度里唯独 L4
 * 没有实现。本校验是<b>纯确定性计算</b>，不依赖模型、标注数据或外部服务： 明细求和与总额字段比较，超出 1 分容差即把总额字段的校验状态降为 {@code FAILED}， 由策略层「L2
 * 确定性规则优先」的前置闸门直接转人工复核。
 *
 * <p>总额字段<b>复用版式契约唯一事实源</b>（{@link ClaimDocumentTemplateRegistry} 中 {@code total_amount}
 * 的别名清单），不另写一份字段名，避免「同一语义两处配置源＝配了不生效」。
 *
 * <p><b>误报控制</b>：无明细、无总额声明、命中总额字段不恰好一个、金额不可解析、 明细含非法值时，一律返回 {@code NOT_APPLICABLE} 语义（保留原证据链 +
 * 审计说明）， 绝不批量制造假红、诱使使用者关掉闸门。
 */
public class L4BusinessConsistencyValidator {

  /** 金额容差：1 分 + 浮点余量。 */
  private static final double TOLERANCE = 0.011;

  private static final String TOTAL_CANONICAL = "total_amount";

  private final ClaimDocumentTemplateRegistry templates;

  public L4BusinessConsistencyValidator(ClaimDocumentTemplateRegistry templates) {
    this.templates = templates == null ? ClaimDocumentTemplateRegistry.noTemplates() : templates;
  }

  /**
   * @param fields 可能已按 L4 结论覆写校验状态的证据链 @param reasons 逐字段审计说明
   */
  public record Outcome(List<OcrFieldEvidence> fields, Map<String, String> reasons) {}

  /** 在 L2 之后、L3 质量评估之前执行；L4 结论会改变总额字段的校验状态，从而影响质量分与路由。 */
  public Outcome validate(OcrResult result, List<OcrFieldEvidence> validated, OcrDocType docType) {
    List<OcrFieldEvidence> base = validated == null ? List.of() : validated;
    if (result == null || result.lineItems() == null || result.lineItems().isEmpty()) {
      return new Outcome(base, Map.of());
    }

    double sum = 0.0;
    for (OcrLineItem item : result.lineItems()) {
      double amount = item.amount();
      if (!Double.isFinite(amount) || amount < 0.0) {
        return new Outcome(base, Map.of("[L4]", "费用明细含非法金额 " + amount + "，跳过汇总一致性校验（保留原证据链）"));
      }
      sum += amount;
    }

    OcrFieldEvidence total = findTotalField(base, docType);
    if (total == null) {
      return new Outcome(base, Map.of("[L4]", "未唯一确定总额字段，跳过汇总一致性校验（保留原证据链）"));
    }
    double parsed = parseAmount(total.value());
    if (Double.isNaN(parsed)) {
      return new Outcome(base, Map.of("[L4]", "总额字段值不可解析为金额: " + total.value() + "，跳过校验"));
    }

    double diff = Math.abs(sum - parsed);
    Map<String, String> reasons = new LinkedHashMap<>();
    if (diff <= TOLERANCE) {
      reasons.put(
          total.fieldName(), "[L4] 明细汇总 " + fmt(sum) + " 与总额 " + fmt(parsed) + " 一致（容差 0.01）");
      return new Outcome(base, reasons);
    }

    List<OcrFieldEvidence> updated = new ArrayList<>();
    for (OcrFieldEvidence e : base) {
      updated.add(e == total ? total.withValidationStatus(ValidationStatus.FAILED) : e);
    }
    reasons.put(
        total.fieldName(),
        "[L4] 明细汇总 "
            + fmt(sum)
            + " 与总额 "
            + fmt(parsed)
            + " 不一致（差 "
            + fmt(diff)
            + " > 0.01），总额字段降级 FAILED 并强制人工复核");
    return new Outcome(updated, reasons);
  }

  /** 通过模板声明的别名精确匹配，命中不恰好一个时返回 {@code null}（不猜测哪个是总额）。 */
  private OcrFieldEvidence findTotalField(List<OcrFieldEvidence> fields, OcrDocType docType) {
    OcrFieldEvidence found = null;
    for (RequiredField required : templates.templateFor(docType).requiredFields()) {
      if (!TOTAL_CANONICAL.equals(required.canonicalName())) {
        continue;
      }
      for (OcrFieldEvidence e : fields) {
        if (required.matches(ClaimDocumentTemplate.normalize(e.fieldName()))) {
          if (found != null) {
            return null;
          }
          found = e;
        }
      }
    }
    return found;
  }

  /** 与 {@link NumericAmountRule} 同口径：剔除货币符号/千分位，仅接受非负、最多两位小数。 */
  private static double parseAmount(String value) {
    if (value == null || value.isBlank()) {
      return Double.NaN;
    }
    String cleaned = value.replaceAll("[\\s¥￥$,]", "");
    if (cleaned.isEmpty() || cleaned.startsWith("-") || !cleaned.matches("\\d+(\\.\\d{1,2})?")) {
      return Double.NaN;
    }
    try {
      return Double.parseDouble(cleaned);
    } catch (NumberFormatException e) {
      return Double.NaN;
    }
  }

  private static String fmt(double v) {
    return String.format(Locale.ROOT, "%.2f", v);
  }
}
