package com.bone.claim.domain.model.decision;

import java.util.List;

/**
 * AI 认知层差异提示受控码（唯一真源）。
 *
 * <p>网关适配器用它做输出白名单，决策图用它做流程路由——同一个集合，避免「模型输出了新码、 网关放行但决策图不认」的两处漂移。自由文本差异提示一律不得进入本集合。
 *
 * <p><b>{@link #ALL} 的顺序即处置优先级</b>（下标越小越优先）：路由收到多个差异码时按此顺序取主码，
 * 使「同一组差异」恒得同一处置，与模型输出的数组顺序无关（模型给数组排序没有语义）。
 */
public final class AiDiscrepancyCodes {

  public static final String AMOUNT_MISMATCH = "AMOUNT_MISMATCH";
  public static final String DIAGNOSIS_FEE_MISMATCH = "DIAGNOSIS_FEE_MISMATCH";
  public static final String DRUG_INDICATION_MISMATCH = "DRUG_INDICATION_MISMATCH";
  public static final String EVIDENCE_MISSING = "EVIDENCE_MISSING";
  public static final String DUPLICATE_ITEM = "DUPLICATE_ITEM";
  public static final String OTHER = "OTHER";

  /**
   * 原因码落库列宽（{@code claim_decision_execution.triage_reason VARCHAR(32)}）。
   *
   * <p><b>为什么列宽要写进 domain</b>：MySQL 在 {@code STRICT_TRANS_TABLES} 下对超长值不是静默截断，而是 <b>整条语句报 1406
   * 并回滚</b>——即「AI 提示了一个合法差异码」会变成「决策执行写不进去」，在自动通过路径上抛 500。 列宽不是纯 DDL 细节，它是本原因码格式的硬约束，故由单测对 {@link
   * #ALL} 逐条断言 （{@code AiDiscrepancyRoutingTest#reasonCodesFitTriageReasonColumn}）。
   */
  public static final int TRIAGE_REASON_MAX_LENGTH = 32;

  public static final List<String> ALL =
      List.of(
          EVIDENCE_MISSING,
          AMOUNT_MISMATCH,
          DIAGNOSIS_FEE_MISMATCH,
          DRUG_INDICATION_MISMATCH,
          DUPLICATE_ITEM,
          OTHER);

  private AiDiscrepancyCodes() {}
}
