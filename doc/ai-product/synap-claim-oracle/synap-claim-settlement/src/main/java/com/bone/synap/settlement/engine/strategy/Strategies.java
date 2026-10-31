package com.bone.synap.settlement.engine.strategy;

import com.bone.synap.settlement.Money;
import com.bone.synap.settlement.domain.ClaimContext;
import com.bone.synap.settlement.domain.FormulaSnapshot;

import java.util.ArrayList;
import java.util.List;

/**
 * 三类责任理算策略（复用 proven tpa-saas 模式：三类责任 + 工厂分发），并按 synapClaim 架构约束补强：
 *   1) 纯函数、无 Spring/DB/Feign 依赖（端口防腐，外部系统一律经 port）；
 *   2) 策略只产出「可赔基数 base + 比例 + 理算层面免赔」，**免赔额/限额/控额由引擎统一按正确顺序施加**
 *      （对齐 Guidewire 规则引擎/计算引擎分离；修正「免赔后置于比例」这一金额语义缺陷）；
 *   3) 赔付比例硬护栏 ≤100%（含 AI 加成后），杜绝超赔；
 *   4) AI 加成仅经 confirmed 护栏进入。
 *
 * 理算顺序（业界通行，泰康/平安条款 + Guidewire）：
 *   原始费用 → 目录/合理必要筛选 → 发票层面免赔 → 第三方已付抵扣 → 年度免赔 → 赔付比例
 *   → 理算层面免赔 → 四舍五入 → 多维度 min 控额
 */
public final class Strategies {

    public interface LiabilityStrategy {
        FormulaSnapshot.LiabilityType supports();
        List<RawLine> compute(ClaimContext ctx, FormulaSnapshot snap,
                              boolean requireConfirm, boolean clampRatioAt100, Money aiBonusPct);
    }

    /** 策略产出的「控额前」原始行：只含基数与比例，不含免赔/限额结论。 */
    public static final class RawLine {
        public final String feeType;
        public final Money origin;       // 原始费用（已钳负）
        public final Money base;         // 可赔基数 = clamp(origin − 发票层面免赔, 0)
        public final Money ratioPct;     // 赔付比例（百分比，已护栏 ≤100）
        public final Money deductAdjust; // 理算层面免赔（比例后扣，绝对额）
        public final String rejectType;  // 目录外/免责：非空即该行不赔

        public RawLine(String feeType, Money origin, Money base, Money ratioPct,
                       Money deductAdjust, String rejectType) {
            this.feeType = feeType;
            this.origin = origin;
            this.base = base;
            this.ratioPct = ratioPct;
            this.deductAdjust = deductAdjust == null ? Money.ZERO : deductAdjust;
            this.rejectType = rejectType;
        }
    }

    /** 定额型：无免赔、无比例，固定给付。 */
    public static final class FixedStrategy implements LiabilityStrategy {
        public FormulaSnapshot.LiabilityType supports() { return FormulaSnapshot.LiabilityType.FIXED; }
        public List<RawLine> compute(ClaimContext ctx, FormulaSnapshot snap,
                                     boolean requireConfirm, boolean clampRatioAt100, Money aiBonusPct) {
            List<RawLine> out = new ArrayList<>();
            out.add(new RawLine("FIXED", snap.fixedAmount, snap.fixedAmount, Money.of(100), Money.ZERO, null));
            return out;
        }
    }

    /** 津贴型：按天/次给付，受单次限额 min（次限额属责任条款，策略内取交）。 */
    public static final class AllowanceStrategy implements LiabilityStrategy {
        public FormulaSnapshot.LiabilityType supports() { return FormulaSnapshot.LiabilityType.ALLOWANCE; }
        public List<RawLine> compute(ClaimContext ctx, FormulaSnapshot snap,
                                     boolean requireConfirm, boolean clampRatioAt100, Money aiBonusPct) {
            List<RawLine> out = new ArrayList<>();
            Money current = snap.dailyAmount.multiply(Money.of(snap.days));
            Money base = current.min(snap.perTimeLimit);
            out.add(new RawLine("ALLOWANCE", current, base, Money.of(100), Money.ZERO, null));
            return out;
        }
    }

    /** 报销型：逐费用段产出「可赔基数 + 比例 + 理算层面免赔」。 */
    public static final class ReimbursementStrategy implements LiabilityStrategy {
        public FormulaSnapshot.LiabilityType supports() { return FormulaSnapshot.LiabilityType.REIMBURSEMENT; }
        public List<RawLine> compute(ClaimContext ctx, FormulaSnapshot snap,
                                     boolean requireConfirm, boolean clampRatioAt100, Money aiBonusPct) {
            List<RawLine> out = new ArrayList<>();
            for (FormulaSnapshot.FeeConfig fc : snap.feeConfigs.values()) {
                Money origin = ctx.invoiceFees.getOrDefault(fc.feeType, Money.ZERO).clampNonNegative();
                Money ratioPct = fc.payRatioPct;

                // AI 加成护栏：仅 confirmed 建议可提升比例（未确认绝不采用）
                boolean bonus = requireConfirm
                        ? ctx.confirmedBonus(fc.feeType) != null
                        : ctx.anyBonus(fc.feeType) != null;
                if (bonus) ratioPct = ratioPct.add(aiBonusPct);

                // 硬护栏：赔付比例 ≤100%（含 AI 加成后），杜绝超赔（保险法/条款上限）
                if (clampRatioAt100) ratioPct = ratioPct.clampMax(Money.of(100)).clampNonNegative();

                // 拒赔行（丙类/免责）：基数归零，仍保留原始费用用于告知与对账
                Money base = fc.rejectType != null
                        ? Money.ZERO
                        : origin.subtract(fc.deductInvoice).clampNonNegative();

                out.add(new RawLine(fc.feeType, origin, base, ratioPct, fc.deductAdjust, fc.rejectType));
            }
            return out;
        }
    }
}
