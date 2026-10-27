package com.bone.synap.settlement.domain;

import com.bone.synap.settlement.Money;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 理算输入上下文（纯数据，无副作用）。
 * 对应《理算引擎内部设计》§3.4「AI 仅输入侧」护栏：AI 建议（如费用项→目录编码、
 * 责任适用性、加成建议）以 AiSuggestion 携带 confirmed 标志，未确认前引擎绝不采用。
 *
 * 业界最佳实践补齐的输入维度：
 *   policyId / periodKey  ——「保单+账期」，用于年度免赔额 / 年度限额的跨案累计
 *                            （对齐 Guidewire Accumulation / ISO 年度免赔累计）；
 *   thirdPartyPaid        —— 社保统筹支付 + 其他保险已赔金额。**损失补偿原则（不可获利）**：
 *                            被保险人不得因理赔获利，已获第三方补偿部分必须先从可赔基数中扣除
 *                            （保险法第五十五条 / ISO「Other Insurance」）；
 *   fraudHold             —— 反欺诈（FraudScreen 节点）判定 P0 高风险。理算须 fail-closed：
 *                            不出金额、不占额度、不消费累计，直接挂起人工（平安/太保风控红线）。
 */
public final class ClaimContext {
    public final String claimId;
    public final String tenantId;
    public final String planId;
    public final String policyId;          // 保单号（跨案累计键）
    public final String periodKey;         // 账期（如 "2026"）
    public final Map<String, Money> invoiceFees; // 费用类型编码 -> 原始金额
    public final Money planRemaining;             // 计划维度剩余控额
    public final Money annualDeductible;         // 年度免赔额（0=无）
    public final Money annualLimit;               // 年度赔付限额（0=无）
    public final List<AiSuggestion> aiSuggestions;
    /** 整案拒赔原因（非空即整案 REJECT）：等待期内/免责条款/指定医院外等。 */
    public final List<String> denyReasons;
    /** 第三方已付（社保统筹支付 + 其他保险已赔）：不可获利原则下先从可赔基数扣除。 */
    public final Money thirdPartyPaid;
    /** 反欺诈 P0 命中：引擎 fail-closed 挂起，不产生可支付结论。 */
    public final boolean fraudHold;

    public ClaimContext(String claimId, String tenantId, String planId, String policyId, String periodKey,
                        Map<String, Money> invoiceFees, Money planRemaining,
                        Money annualDeductible, Money annualLimit,
                        List<AiSuggestion> aiSuggestions) {
        this(claimId, tenantId, planId, policyId, periodKey, invoiceFees, planRemaining,
                annualDeductible, annualLimit, aiSuggestions, List.of(), Money.ZERO, false);
    }

    public ClaimContext(String claimId, String tenantId, String planId, String policyId, String periodKey,
                        Map<String, Money> invoiceFees, Money planRemaining,
                        Money annualDeductible, Money annualLimit,
                        List<AiSuggestion> aiSuggestions, List<String> denyReasons) {
        this(claimId, tenantId, planId, policyId, periodKey, invoiceFees, planRemaining,
                annualDeductible, annualLimit, aiSuggestions, denyReasons, Money.ZERO, false);
    }

    public ClaimContext(String claimId, String tenantId, String planId, String policyId, String periodKey,
                        Map<String, Money> invoiceFees, Money planRemaining,
                        Money annualDeductible, Money annualLimit,
                        List<AiSuggestion> aiSuggestions, List<String> denyReasons,
                        Money thirdPartyPaid, boolean fraudHold) {
        this.claimId = claimId;
        this.tenantId = tenantId;
        this.planId = planId;
        this.policyId = policyId;
        this.periodKey = periodKey;
        this.invoiceFees = invoiceFees;
        this.planRemaining = planRemaining;
        this.annualDeductible = annualDeductible;
        this.annualLimit = annualLimit;
        this.aiSuggestions = aiSuggestions;
        this.denyReasons = denyReasons;
        this.thirdPartyPaid = thirdPartyPaid == null ? Money.ZERO : thirdPartyPaid;
        this.fraudHold = fraudHold;
    }

    /** AI 建议：对某个费用类型提议「加成 10% 比例」。必须经人工/规则确认后方可采用。 */
    public static final class AiSuggestion {
        public final String feeType;
        public final boolean bonus;      // 提议加成
        public final boolean confirmed;  // 是否已确认

        public AiSuggestion(String feeType, boolean bonus, boolean confirmed) {
            this.feeType = feeType;
            this.bonus = bonus;
            this.confirmed = confirmed;
        }

        @Override public String toString() { return feeType + "/bonus=" + bonus + "/confirmed=" + confirmed; }
    }

    /** 仅返回「已确认」的加成建议（护栏核心）。 */
    public AiSuggestion confirmedBonus(String feeType) {
        for (AiSuggestion s : aiSuggestions) {
            if (s.feeType.equals(feeType) && s.bonus && s.confirmed) return s;
        }
        return null;
    }

    /** 返回任意加成建议（含未确认）——仅供 requireConfirm=false 的变种比对。 */
    public AiSuggestion anyBonus(String feeType) {
        for (AiSuggestion s : aiSuggestions) {
            if (s.feeType.equals(feeType) && s.bonus) return s;
        }
        return null;
    }

    /**
     * 输入指纹（幂等键组成部分）。包含全部会改变理算结果的输入维度——
     * 同一 claimId 但输入不同（如补交发票、AI 建议状态变化）必须算作**不同**的理算请求。
     */
    public String inputHash() {
        StringBuilder sb = new StringBuilder();
        sb.append(planId).append('|').append(policyId).append('|').append(periodKey).append('|');
        new java.util.TreeMap<>(invoiceFees).forEach((k, v) -> sb.append(k).append('=').append(v).append(';'));
        sb.append('|').append(planRemaining)
          .append('|').append(annualDeductible).append('|').append(annualLimit)
          .append('|').append(thirdPartyPaid)
          .append('|').append(fraudHold).append('|').append(denyReasons)
          .append('|').append(aiSuggestions);
        return sb.toString();
    }
}
