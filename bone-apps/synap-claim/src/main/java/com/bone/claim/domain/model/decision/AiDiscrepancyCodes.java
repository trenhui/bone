package com.bone.claim.domain.model.decision;

import java.util.List;

/**
 * AI 认知层差异提示受控码（唯一真源）。
 *
 * <p>网关适配器用它做输出白名单，决策图用它做流程路由——同一个集合，避免「模型输出了新码、 网关放行但决策图不认」的两处漂移。自由文本差异提示一律不得进入本集合。
 */
public final class AiDiscrepancyCodes {

  public static final String AMOUNT_MISMATCH = "AMOUNT_MISMATCH";
  public static final String DIAGNOSIS_FEE_MISMATCH = "DIAGNOSIS_FEE_MISMATCH";
  public static final String DRUG_INDICATION_MISMATCH = "DRUG_INDICATION_MISMATCH";
  public static final String EVIDENCE_MISSING = "EVIDENCE_MISSING";
  public static final String DUPLICATE_ITEM = "DUPLICATE_ITEM";
  public static final String OTHER = "OTHER";

  public static final List<String> ALL =
      List.of(
          AMOUNT_MISMATCH,
          DIAGNOSIS_FEE_MISMATCH,
          DRUG_INDICATION_MISMATCH,
          EVIDENCE_MISSING,
          DUPLICATE_ITEM,
          OTHER);

  private AiDiscrepancyCodes() {}
}
