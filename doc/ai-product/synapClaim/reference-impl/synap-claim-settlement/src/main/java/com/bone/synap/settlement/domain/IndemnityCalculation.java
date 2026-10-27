package com.bone.synap.settlement.domain;

import com.bone.synap.settlement.Money;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 理算聚合根（A6）。
 * 一次理算请求产出唯一的 IndemnityCalculation，包含：
 *   - 单费用成分行 lines（六成分 + rejectType，见《内部设计》§3.2）；
 *   - totalPayout = 各行赔付额之和（gross，满足归因完备 P8，含拒赔行 payout=0）；
 *   - netPayout   = 经保单级跨次累计（年度免赔/年度限额/责任限额）后的实际应付；
 *   - status      = 理算结论（PAY / PARTIAL / REJECT / HOLD）；
 *   - ruleTrace   = 每条费用行命中规则 ID 清单（可解释性 / 规则溯源）；
 *   - explain     = 人类可读的逐行赔付说明（监管告知 / 客户沟通）。
 *
 * 冲正（reversal）契约：结论必须自携带 policyId / periodKey / 三项占用额，
 * 以便重算或撤件时原样回退累计占用（诊断 N2/N8，对应 Guidewire reversal）。
 */
public final class IndemnityCalculation {

    /**
     * 理算结论：
     *   PAY     = 全额/部分应付（已足额核定）；
     *   PARTIAL = 有削减（费用级拒赔或限额截断）或当期应付为 0（免赔/限额耗尽）；
     *   REJECT  = 整案拒赔（等待期/免责/指定医院外等前置核验不通过）；
     *   HOLD    = 风控挂起（反欺诈 P0）：fail-closed，不出金额、不占额度。
     */
    public enum SettlementStatus { PAY, PARTIAL, REJECT, HOLD }

    public final String claimId;
    public final String tenantId;
    /** 保单号 + 账期：结论自携带，供冲正定位累计键（诊断 N2）。 */
    public final String policyId;
    public final String periodKey;
    public final List<AdjustmentLine> lines;
    public final Money totalPayout;            // gross = Σ lines.feePayout
    public final Money netPayout;              // 经跨案累计后实际应付
    public final Money annualDeductibleApplied;// 本次消抵的年度免赔额
    public final Money annualLimitApplied;     // 本次占用的年度限额
    public final Money liabilityApplied;       // 本次占用的责任限额（责任维度亦跨案累计）
    public final Money thirdPartyApplied;      // 第三方已付抵扣额（不可获利原则）
    public final Map<String, List<String>> ruleTrace; // feeType -> 命中规则 ID
    public final String explain;               // 人类可读说明
    public final long snapshotVersion;
    public final String traceId;
    public final SettlementStatus status;      // 理算结论
    public final boolean denied;               // 是否整案拒赔
    public final List<String> denyReasons;     // 拒赔原因清单（REJECT 时非空）

    private IndemnityCalculation(String claimId, String tenantId, String policyId, String periodKey,
                                 List<AdjustmentLine> lines, Money totalPayout, Money netPayout,
                                 Money annualDeductibleApplied, Money annualLimitApplied, Money liabilityApplied,
                                 Map<String, List<String>> ruleTrace, String explain,
                                 long snapshotVersion, String traceId,
                                 SettlementStatus status, boolean denied, List<String> denyReasons,
                                 Money thirdPartyApplied) {
        this.claimId = claimId;
        this.tenantId = tenantId;
        this.policyId = policyId;
        this.periodKey = periodKey;
        this.lines = lines;
        this.totalPayout = totalPayout;
        this.netPayout = netPayout;
        this.annualDeductibleApplied = annualDeductibleApplied;
        this.annualLimitApplied = annualLimitApplied;
        this.liabilityApplied = liabilityApplied == null ? Money.ZERO : liabilityApplied;
        this.ruleTrace = ruleTrace;
        this.explain = explain;
        this.snapshotVersion = snapshotVersion;
        this.traceId = traceId;
        this.status = status;
        this.denied = denied;
        this.denyReasons = denyReasons;
        this.thirdPartyApplied = thirdPartyApplied == null ? Money.ZERO : thirdPartyApplied;
    }

    public static IndemnityCalculation of(String claimId, String tenantId,
                                         List<AdjustmentLine> lines, Money netPayout,
                                         Money annualDeductibleApplied, Money annualLimitApplied,
                                         Map<String, List<String>> ruleTrace, String explain,
                                         long snapshotVersion, String traceId) {
        return of(claimId, tenantId, null, null, lines, netPayout, annualDeductibleApplied,
                annualLimitApplied, Money.ZERO, ruleTrace, explain, snapshotVersion, traceId,
                SettlementStatus.PAY, false, List.of(), Money.ZERO);
    }

    /** 挂起/拒赔等无行结论（风控 HOLD）。 */
    public static IndemnityCalculation hold(String claimId, String tenantId, String explain,
                                            long snapshotVersion, String traceId) {
        return of(claimId, tenantId, null, null, List.of(), Money.ZERO, Money.ZERO, Money.ZERO,
                Money.ZERO, Map.of(), explain, snapshotVersion, traceId,
                SettlementStatus.HOLD, false, List.of(), Money.ZERO);
    }

    public static IndemnityCalculation of(String claimId, String tenantId, String policyId, String periodKey,
                                         List<AdjustmentLine> lines, Money netPayout,
                                         Money annualDeductibleApplied, Money annualLimitApplied,
                                         Money liabilityApplied,
                                         Map<String, List<String>> ruleTrace, String explain,
                                         long snapshotVersion, String traceId,
                                         SettlementStatus status, boolean denied, List<String> denyReasons,
                                         Money thirdPartyApplied) {
        Money gross = Money.ZERO;
        for (AdjustmentLine l : lines) gross = gross.add(l.feePayout);
        return new IndemnityCalculation(claimId, tenantId, policyId, periodKey,
                new ArrayList<>(lines), gross, netPayout, annualDeductibleApplied, annualLimitApplied,
                liabilityApplied, ruleTrace, explain, snapshotVersion, traceId,
                status, denied, denyReasons, thirdPartyApplied);
    }
}
