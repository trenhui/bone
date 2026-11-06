package com.bone.aidoc.domain.gateway;

import com.bone.aidoc.domain.model.OcrDocType;
import com.bone.aidoc.domain.model.OcrFieldEvidence;
import com.bone.aidoc.domain.model.ValidationResult;
import java.util.Set;

/**
 * 字段级规则校验端口（ADR-0046 五层可信度 L2：金额大小写、日期格式、校验位等确定性规则）。
 *
 * <p>规则校验是可解释、可审计的强信号，应优先于「学出来的质检模型」使用。 每条规则声明适用票种与字段，
 * 引擎只对其覆盖范围内的字段调用。骨架可由基础设施层注册具体规则（如金额大小写一致性、身份证校验位）。
 */
public interface FieldValidationRule {

  /** 规则标识（如 amount-case-consistency / id-card-check-digit）。 */
  String ruleId();

  /** 适用文档类型（空集表示通用）。 */
  Set<OcrDocType> appliesTo();

  /** 是否适用于该字段（按字段名过滤，避免无关规则误伤）。默认全适用。 */
  default boolean appliesToField(OcrFieldEvidence field) {
    return true;
  }

  /** 校验单字段，返回状态与说明。 */
  ValidationResult validate(OcrFieldEvidence field, OcrDocType docType);
}
