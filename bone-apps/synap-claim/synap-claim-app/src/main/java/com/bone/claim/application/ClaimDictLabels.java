package com.bone.claim.application;

import java.util.Map;

/**
 * 受控码 → 中文标签的<b>唯一真源</b>（后端持有）。
 *
 * <p>此前这些标签散落在前端 6 个字典文件里逐字手抄，后端新增码而前端漏跟随时只会显示原始码 （{@code CLAIM_STATUS_META}
 * 注释已记录过该漂移坑）。现统一收回后端：新增/改名受控码只改此处与对应枚举/常量， 前端经 codegen 重新生成即全量同步。
 *
 * <p>{@code ClaimStatus} 的标签直接来自枚举 {@code displayName()}，不在此重复；其余分类的中文在此集中维护。
 */
public final class ClaimDictLabels {

  private ClaimDictLabels() {}

  /** AI 参与四态标签（短展示名，对齐 AiDecisionDisclosure.LABEL_* 语义）。 */
  public static final Map<String, String> AI_INVOLVEMENT =
      Map.ofEntries(
          Map.entry("AI_ASSISTED", "含 AI 辅助生成内容"),
          Map.entry("AI_STUB", "未使用真实模型（确定性替身）"),
          Map.entry("AI_UNAVAILABLE", "AI 能力未可用（规则降级）"),
          Map.entry("UNDETERMINED", "无法确认 AI 是否参与"));

  /** PIPL 第二十四条 / 金发 8 号权利标签。 */
  public static final Map<String, String> AI_RIGHTS =
      Map.ofEntries(
          Map.entry("REQUEST_HUMAN_REVIEW", "要求人工复核"),
          Map.entry("APPEAL_WITHIN_48H", "48 小时内申诉"),
          Map.entry("EXPLANATION_OF_DECISION", "要求说明决定依据"));

  /** 自动放款消费结果标签（AutoApprovalOutcome.reason）。 */
  public static final Map<String, String> CONSUMPTION_REASON =
      Map.ofEntries(
          Map.entry("APPLIED", "已自动放款"),
          Map.entry("AUTO_APPROVE_TOGGLE_OFF", "自动放款开关关闭"),
          Map.entry("NO_DECISION", "无决策记录"),
          Map.entry("CLAIM_NOT_FOUND", "案件不存在"),
          Map.entry("ALREADY_SETTLED", "案件已结案"),
          Map.entry("CAP_EXCEEDED", "超单笔上限"),
          Map.entry("DUAL_REVIEW_REQUIRED", "超双人复核门转双人"),
          Map.entry("RISK_GATE_BLOCKED", "风险闸拦截"));

  /** 理算零赔付归因标签（AdjustmentEngine.ZERO_*）。 */
  public static final Map<String, String> ZERO_REASON =
      Map.ofEntries(
          Map.entry("ZERO_REASONABLE", "合理费用为 0（费用全部被判定为不合理/不可赔）"),
          Map.entry("ZERO_DEDUCT", "免赔未达（扣减免赔额后为 0）"),
          Map.entry("ZERO_PERCENT", "赔付比例为 0"),
          Map.entry("ZERO_LIMIT", "责任额度不足（本行额度已用尽）"),
          Map.entry("ZERO_LIMIT_EXHAUSTED", "责任额度已耗尽（历史占额已封顶）"),
          Map.entry("ZERO_NOT_COVERED", "不在保障范围"));

  /** 人工拒赔数字受控码标签（rejectionCodeOf 派生）。 */
  public static final Map<String, String> REJECT_REASON =
      Map.ofEntries(
          Map.entry("03", "03 就诊医院不符合责任医院规则"),
          Map.entry("04", "04 诊断不符合责任免赔规则"),
          Map.entry("05", "05 合理费用 / 赔付比例为 0（不合理费用、非保障范围）"),
          Map.entry("06", "06 未达免赔额（免赔未消抵完）"),
          Map.entry("09", "09 无剩余保额（责任额度已用尽/封顶）"),
          Map.entry("20", "20 目录外费用不赔付"),
          Map.entry("30", "30 责任范围除外"),
          Map.entry("40", "40 风险标记（转人工核实）"));

  /** AI 结构化拒赔原因码标签（RejectReasonCodes 白名单）。 */
  public static final Map<String, String> AI_REJECT_REASON =
      Map.ofEntries(
          Map.entry("EVIDENCE_INSUFFICIENT", "证据不足"),
          Map.entry("CLINICAL_UNREASONABLE", "临床不合理"),
          Map.entry("POLICY_EXCLUSION", "保单责任免除"),
          Map.entry("AMOUNT_MISMATCH", "金额不符"),
          Map.entry("FRAUD_SUSPECTED", "疑似欺诈"),
          Map.entry("OTHER", "其他（需人工补充）"));

  /** 反欺诈硬闸未过原因码标签（RuleCheckNodeHandler#fraudGateReason）。 */
  public static final Map<String, String> FRAUD_GATE_REASON =
      Map.ofEntries(
          Map.entry("ANTI_FRAUD_CAPABILITY_MISSING", "增强反欺诈能力未集成（转人工）"),
          Map.entry("RISK_HIT", "命中风险"),
          Map.entry("VERIFY_NOT_PASSED", "验真未通过"),
          Map.entry("PASSED", "通过"));

  /** 方法级权限码标签（@PreAuthorize）。 */
  public static final Map<String, String> PERMISSION =
      Map.ofEntries(
          Map.entry("claim:policy:read", "责任/保单配置读"),
          Map.entry("claim:policy:write", "责任/保单配置写"),
          Map.entry("claim:mapping:dispose", "映射处置"),
          Map.entry("claim:quota:read", "额度读"),
          Map.entry("claim:quota:write", "额度写"),
          Map.entry("claim:claims:read", "案件读"),
          Map.entry("claim:claims:write", "案件写"),
          Map.entry("claim:review:write", "复核写"),
          Map.entry("claim:decision:write", "核赔写"),
          Map.entry("claim:settlement:write", "结算写"),
          Map.entry("claim:appeal:write", "申诉写"),
          Map.entry("claim:inspection:write", "后督写"));
}
