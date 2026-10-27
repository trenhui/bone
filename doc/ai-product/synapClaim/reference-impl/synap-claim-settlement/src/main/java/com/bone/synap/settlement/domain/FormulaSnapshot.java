package com.bone.synap.settlement.domain;

import com.bone.synap.settlement.Money;

import java.util.List;
import java.util.Map;

/**
 * 理算公式快照（物化契约）。
 *
 * 这是「修正」tpa-saas 实时读 plan.version 重算漂移债的关键：
 * 引擎只消费冻结的快照，源配置（plan/liability/catalog）变更不影响已定快照的复算结果。
 * 对应《理算引擎内部设计》§3.3 FormulaSnapshot + 防假绿探针 P2（快照复算）。
 *
 * 设计取舍（业界基准：Guidewire CalculationEngine 公式固化 / SAP MDG 解耦）：
 *   - 责任类型与额度、免赔、赔付比例、费用子限额、目录版本全部冻结进快照；
 *   - 不再在理算时回查 DB（tpa-saas 的 plan.version 实时读即此债）；
 *   - snapshotVersion 用于审计回溯与「同输入同输出」可复算证明。
 */
public final class FormulaSnapshot {

    public enum LiabilityType { FIXED, ALLOWANCE, REIMBURSEMENT }

    public final String liabilityId;
    public final String liabilityName;
    public final LiabilityType type;

    // 通用
    public final Money liabilityLimit;       // 责任剩余总控额（快照生成时冻结）
    // FIXED
    public final Money fixedAmount;          // 定额给付
    // ALLOWANCE
    public final Money dailyAmount;          // 每日/每次津贴
    public final Money perTimeLimit;         // 单次/单日限额
    public final int days;                    // 给付天数/次数
    // REIMBURSEMENT
    public final Map<String, FeeConfig> feeConfigs; // 费用类型 -> 配置

    public final String catalogVersion;      // 目录编码版本（冻结）
    public final long snapshotVersion;       // 快照版本号（审计/复算）

    public FormulaSnapshot(String liabilityId, String liabilityName, LiabilityType type,
                           Money liabilityLimit, Money fixedAmount, Money dailyAmount,
                           Money perTimeLimit, int days, Map<String, FeeConfig> feeConfigs,
                           String catalogVersion, long snapshotVersion) {
        this.liabilityId = liabilityId;
        this.liabilityName = liabilityName;
        this.type = type;
        this.liabilityLimit = liabilityLimit;
        this.fixedAmount = fixedAmount;
        this.dailyAmount = dailyAmount;
        this.perTimeLimit = perTimeLimit;
        this.days = days;
        this.feeConfigs = feeConfigs;
        this.catalogVersion = catalogVersion;
        this.snapshotVersion = snapshotVersion;
    }

    /**
     * 公式指纹（幂等键组成部分）：把「会改变理算结果」的全部快照维度编码。
     * 源配置变更（比例/限额/免赔/目录版本）必然改变指纹，从而触发重新理算而非返回旧结论——
     * 这是「快照冻结」与「幂等复用」两条护栏能共存的关键（P2 快照复算探针依赖此性质）。
     */
    public String formulaHash() {
        StringBuilder sb = new StringBuilder();
        sb.append(liabilityId).append('|').append(type).append('|').append(liabilityLimit)
          .append('|').append(fixedAmount).append('|').append(dailyAmount)
          .append('|').append(perTimeLimit).append('|').append(days)
          .append('|').append(catalogVersion).append('|').append(snapshotVersion).append('|');
        if (feeConfigs != null) {
            new java.util.TreeMap<>(feeConfigs).forEach((k, v) ->
                    sb.append(k).append('=').append(v.payRatioPct).append('/').append(v.feeSubLimit)
                      .append('/').append(v.deductInvoice).append('/').append(v.deductAdjust)
                      .append('/').append(v.rejectType).append(';'));
        }
        return sb.toString();
    }

    /** 报销型单费用配置（对应 tpa-saas AdjustmentDetail）。 */
    public static final class FeeConfig {
        public final String feeType;
        public final Money payRatioPct;   // 赔付比例（百分比，如 80 = 80%）
        public final Money feeSubLimit;   // 费用子限额
        public final Money deductInvoice; // 发票层面免赔（绝对额，0=无）
        public final Money deductAdjust;  // 理算层面免赔（绝对额，0=无）
        /** 拒赔类型（目录外/免责项目）：非空表示该费用项不赔，payout=0 并登记 rejectType。
         *  对应真实 TPA 场景：丙类自费不赔、免责条款项目（普康内置医保三大目录 + 泰康责任免除）。 */
        public final String rejectType;

        public FeeConfig(String feeType, Money payRatioPct, Money feeSubLimit,
                         Money deductInvoice, Money deductAdjust) {
            this(feeType, payRatioPct, feeSubLimit, deductInvoice, deductAdjust, null);
        }

        public FeeConfig(String feeType, Money payRatioPct, Money feeSubLimit,
                         Money deductInvoice, Money deductAdjust, String rejectType) {
            this.feeType = feeType;
            this.payRatioPct = payRatioPct;
            this.feeSubLimit = feeSubLimit;
            this.deductInvoice = deductInvoice;
            this.deductAdjust = deductAdjust;
            this.rejectType = rejectType;
        }

        public Money ratioFraction() { return payRatioPct.ratio(); }
    }
}
