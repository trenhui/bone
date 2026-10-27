package com.bone.synap.settlement.diag;

import com.bone.synap.settlement.Money;
import com.bone.synap.settlement.domain.AdjustmentLine;
import com.bone.synap.settlement.domain.ClaimContext;
import com.bone.synap.settlement.domain.FormulaSnapshot;
import com.bone.synap.settlement.domain.IndemnityCalculation;
import com.bone.synap.settlement.engine.SettlementEngine;
import com.bone.synap.settlement.port.Ports;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 第二轮取证诊断：验证上一轮未覆盖的怀疑缺陷。
 * 原则：先打印实测值，再下结论（禁止凭文档宣称判定）。
 */
public final class Diagnostics2 {

    static Ports.InMemoryPorts ports() { return new Ports.InMemoryPorts(); }

    static SettlementEngine engine(Ports.InMemoryPorts p) {
        return new SettlementEngine(p, p, p, p, p, p, p);
    }

    /** 注意：feeSubLimit 传 BIG（=无此项限额）。传 ZERO 会触发 N10 语义缺陷。 */
    static FormulaSnapshot.FeeConfig fee(String t, Money pct) {
        return new FormulaSnapshot.FeeConfig(t, pct, BIG, Money.ZERO, Money.ZERO, null);
    }

    static FormulaSnapshot.FeeConfig rejectFee(String t) {
        return new FormulaSnapshot.FeeConfig(t, Money.of(100), BIG, Money.ZERO, Money.ZERO, "OUT_CATALOG");
    }

    static FormulaSnapshot reimb(Map<String, FormulaSnapshot.FeeConfig> fees, Money liabLimit) {
        return new FormulaSnapshot("L1", "责任L1", FormulaSnapshot.LiabilityType.REIMBURSEMENT,
                liabLimit, Money.ZERO, Money.ZERO, Money.ZERO, 0, fees, "CAT-1", 1L);
    }

    static ClaimContext ctx(String claimId, String policy, Money feeAmt, Money planRemaining,
                            Money annDed, Money annLim) {
        Map<String, Money> f = new LinkedHashMap<>();
        f.put("合理金额", feeAmt);
        return new ClaimContext(claimId, "T1", "PLAN", policy, "2026", f,
                planRemaining, annDed, annLim, List.of());
    }

    static final Money BIG = Money.of(1_000_000);

    static Money abs(Money m) { return m.cmp(Money.ZERO) < 0 ? Money.ZERO.subtract(m) : m; }

    public static void main(String[] args) {
        System.out.println("===== 第二轮取证诊断（质疑上一轮未覆盖的缺陷）=====\n");

        // N1 责任限额是否跨案累计？
        {
            Ports.InMemoryPorts p = ports();
            SettlementEngine e = engine(p);
            Map<String, FormulaSnapshot.FeeConfig> m = new LinkedHashMap<>();
            m.put("合理金额", fee("合理金额", Money.of(100)));
            FormulaSnapshot snap = reimb(m, Money.of(5000)); // 责任限额 5000/年
            IndemnityCalculation c1 = e.settle(ctx("C-1", "POL-A", Money.of(4000), BIG, Money.ZERO, Money.ZERO), snap);
            IndemnityCalculation c2 = e.settle(ctx("C-2", "POL-A", Money.of(4000), BIG, Money.ZERO, Money.ZERO), snap);
            System.out.println("N1 责任限额(年5000)跨案累计");
            System.out.println("   实测: 首案=" + c1.netPayout + " 次案=" + c2.netPayout
                    + " 合计=" + c1.netPayout.add(c2.netPayout));
            System.out.println("   正解: 首案=4000.00 次案=1000.00 合计=5000.00");
            System.out.println("   判定: " + (c2.netPayout.cmp(Money.of(1000)) == 0 ? "OK"
                    : "❌ 缺陷 — 责任限额未跨案累计，超赔 3000") + "\n");
        }

        // N2 重算（补交发票/输入变更）累计是否冲正？
        {
            Ports.InMemoryPorts p = ports();
            SettlementEngine e = engine(p);
            Map<String, FormulaSnapshot.FeeConfig> m = new LinkedHashMap<>();
            m.put("合理金额", fee("合理金额", Money.of(100)));
            FormulaSnapshot snap = reimb(m, BIG);
            IndemnityCalculation c1 = e.settle(ctx("C-1", "POL-B", Money.of(6000), BIG, Money.ZERO, Money.of(10000)), snap);
            IndemnityCalculation c2 = e.settle(ctx("C-1", "POL-B", Money.of(8000), BIG, Money.ZERO, Money.of(10000)), snap);
            Ports.AccUsage u = p.getUsage("T1@POL-B", "2026");
            System.out.println("N2 重算（补交发票 6000→8000）累计冲正");
            System.out.println("   实测: 首次净付=" + c1.netPayout + " 重算净付=" + c2.netPayout
                    + " 账期累计占用=" + u.limitUsed);
            System.out.println("   正解: 重算后累计占用应 = 最新结论 8000.00（旧 6000 须冲正/upsert）");
            System.out.println("   判定: " + (u.limitUsed.cmp(Money.of(8000)) == 0 ? "OK"
                    : "❌ 缺陷 — 累计与最新结论不一致，额度账实不符（实测 " + u.limitUsed + "）") + "\n");
        }

        // N3 预授权是否占用冻结额度？
        {
            Ports.InMemoryPorts p = ports();
            SettlementEngine e = engine(p);
            Map<String, FormulaSnapshot.FeeConfig> m = new LinkedHashMap<>();
            m.put("合理金额", fee("合理金额", Money.of(100)));
            FormulaSnapshot snap = reimb(m, BIG);
            Money total = Money.ZERO;
            StringBuilder sb = new StringBuilder();
            for (int i = 1; i <= 3; i++) {
                SettlementEngine.PreAuthResult r = e.preAuthorizeAndHold(
                        ctx("C-P" + i, "POL-C", Money.ZERO, BIG, Money.ZERO, Money.of(10000)),
                        snap, Money.of(6000), 60_000L);
                total = total.add(r.authorizedAmount);
                sb.append("   第").append(i).append("次预授权=").append(r.authorizedAmount).append("\n");
            }
            System.out.print(sb);
            System.out.println("N3 预授权冻结额度（年限额 10000，3 次各预估 6000）");
            System.out.println("   实测: 累计授权=" + total);
            System.out.println("   正解: 累计授权 ≤ 10000.00（授权须计入 frozen，ACORD 三值额度）");
            System.out.println("   判定: " + (total.cmp(Money.of(10000)) <= 0 ? "OK"
                    : "❌ 缺陷 — 预授权不计冻结，可超年限额授权（医院直付后追收纠纷）") + "\n");
        }

        // N4 预授权责任限额用「总额」还是「剩余」？
        {
            Ports.InMemoryPorts p = ports();
            SettlementEngine e = engine(p);
            Map<String, FormulaSnapshot.FeeConfig> m = new LinkedHashMap<>();
            m.put("合理金额", fee("合理金额", Money.of(100)));
            FormulaSnapshot snap = reimb(m, Money.of(5000));
            e.settle(ctx("C-X", "POL-D", Money.of(3000), BIG, Money.ZERO, Money.of(10000)), snap);
            SettlementEngine.PreAuthResult r = e.preAuthorize(
                    ctx("C-PA", "POL-D", Money.ZERO, BIG, Money.ZERO, Money.of(10000)),
                    snap, Money.of(10000));
            System.out.println("N4 预授权责任限额口径（责任限额 5000，已用 3000）");
            System.out.println("   实测: 授权=" + r.authorizedAmount + " liabilityLimitRemaining=" + r.liabilityLimitRemaining);
            System.out.println("   正解: 授权 ≤ 2000.00（剩余责任限额）");
            System.out.println("   判定: " + (r.authorizedAmount.cmp(Money.of(2000)) <= 0 ? "OK"
                    : "❌ 缺陷 — 预授权按责任总额授权，未扣已用") + "\n");
        }

        // N5 全部费用均拒赔（丙类）时，预授权是否仍授权？
        {
            Ports.InMemoryPorts p = ports();
            SettlementEngine e = engine(p);
            Map<String, FormulaSnapshot.FeeConfig> m = new LinkedHashMap<>();
            m.put("丙类", rejectFee("丙类"));
            FormulaSnapshot snap = reimb(m, BIG);
            SettlementEngine.PreAuthResult r = e.preAuthorize(
                    ctx("C-PB", "POL-E", Money.ZERO, BIG, Money.ZERO, Money.of(10000)),
                    snap, Money.of(8000));
            System.out.println("N5 全目录外（丙类）预授权");
            System.out.println("   实测: 授权=" + r.authorizedAmount + " ratio=" + r.authRatioPct + "%");
            System.out.println("   正解: 授权 = 0.00（无可赔费用）");
            System.out.println("   判定: " + (r.authorizedAmount.isZero() ? "OK"
                    : "❌ 缺陷 — 无可赔费用仍按 100% 授权") + "\n");
        }

        // N6 拒赔行的「免赔额」字段语义
        {
            Ports.InMemoryPorts p = ports();
            SettlementEngine e = engine(p);
            Map<String, FormulaSnapshot.FeeConfig> m = new LinkedHashMap<>();
            m.put("合理金额", fee("合理金额", Money.of(100)));
            m.put("丙类", rejectFee("丙类"));
            FormulaSnapshot snap = reimb(m, BIG);
            Map<String, Money> f = new LinkedHashMap<>();
            f.put("合理金额", Money.of(1000));
            f.put("丙类", Money.of(500));
            ClaimContext c = new ClaimContext("C-RJ", "T1", "PLAN", "POL-F", "2026", f,
                    BIG, Money.ZERO, Money.ZERO, List.of());
            IndemnityCalculation calc = e.settle(c, snap);
            System.out.println("N6 拒赔行免赔额字段语义");
            for (AdjustmentLine l : calc.lines) {
                System.out.println("   " + l.feeType + ": payout=" + l.feePayout
                        + " deduct=" + l.feeDeduct + " reject=" + l.rejectType);
            }
            AdjustmentLine rj = calc.lines.get(1);
            System.out.println("   正解: 拒赔行 deduct = 0.00（拒赔 ≠ 免赔，原因由 rejectType 单列）");
            System.out.println("   判定: " + (rj.feeDeduct.isZero() ? "OK"
                    : "❌ 缺陷 — 拒赔行把原始费用记成免赔额，客户告知书语义误导") + "\n");
        }

        // N7 幂等窗口
        {
            Ports.InMemoryPorts p = ports();
            SettlementEngine e = engine(p);
            Map<String, FormulaSnapshot.FeeConfig> m = new LinkedHashMap<>();
            m.put("合理金额", fee("合理金额", Money.of(100)));
            FormulaSnapshot snap = reimb(m, BIG);
            ClaimContext c = ctx("C-IDEM", "POL-G", Money.of(1000), BIG, Money.ZERO, Money.of(10000));
            e.settle(c, snap);
            int after1 = p.reserveCalls;
            e.settle(c, snap);
            int after2 = p.reserveCalls;
            System.out.println("N7 幂等窗口（顺序执行视角）");
            System.out.println("   实测: 首次后 reserveCalls=" + after1 + "，重复提交后=" + after2);
            System.out.println("   说明: 顺序执行已被幂等挡住；但 find→reserve→save 之间无 CAS 租约，");
            System.out.println("         并发两请求可同时通过 find(null) 各占额一次");
            System.out.println("   判定: " + ((after2 - after1) == 0 ? "顺序 OK / 并发无 CAS 保护（结构性风险）"
                    : "❌ 缺陷") + "\n");
        }

        // N8 撤销/冲正：实证 reverse 能否回退累计
        {
            Ports.InMemoryPorts p = ports();
            SettlementEngine e = engine(p);
            Map<String, FormulaSnapshot.FeeConfig> m = new LinkedHashMap<>();
            m.put("合理金额", fee("合理金额", Money.of(100)));
            FormulaSnapshot snap = reimb(m, BIG);
            IndemnityCalculation c = e.settle(ctx("C-REV", "POL-REV", Money.of(3000), BIG, Money.ZERO, Money.of(10000)), snap);
            Money before = p.getUsage("T1@POL-REV", "2026").limitUsed;
            e.reverseSettlement(c);
            Money after = p.getUsage("T1@POL-REV", "2026").limitUsed;
            System.out.println("N8 撤销/冲正（理赔结论被后督推翻/撤件）");
            System.out.println("   实测: 撤件前累计占用=" + before + " 撤件后=" + after);
            System.out.println("   正解: 撤件后累计归零（Guidewire reversal）");
            System.out.println("   判定: " + (before.cmp(Money.of(3000)) == 0 && after.isZero() ? "OK"
                    : "❌ 缺陷 — 无有效冲正通道") + "\n");
        }

        // N9 逐行取整的分位守恒
        {
            Ports.InMemoryPorts p = ports();
            SettlementEngine e = engine(p);
            Money r333 = Money.of("33.333333");
            Map<String, FormulaSnapshot.FeeConfig> m = new LinkedHashMap<>();
            m.put("A", fee("A", r333));
            m.put("B", fee("B", r333));
            m.put("C", fee("C", r333));
            Map<String, Money> f = new LinkedHashMap<>();
            f.put("A", Money.of(1000)); f.put("B", Money.of(1000)); f.put("C", Money.of(1000));
            ClaimContext c = new ClaimContext("C-RND", "T1", "PLAN", "POL-H", "2026", f,
                    BIG, Money.ZERO, Money.ZERO, List.of());
            IndemnityCalculation calc = e.settle(c, reimb(m, BIG));
            Money exact = Money.of(3000).multiply(r333.ratio()).rounded();
            System.out.println("N9 逐行取整 vs 总额取整（比例 33.333333%，3 行各 1000）");
            System.out.println("   实测: 逐行合计=" + calc.netPayout + "  总额口径=" + exact);
            System.out.println("   判定: " + (calc.netPayout.cmp(exact) == 0 ? "OK"
                    : "❌ 缺陷 — 分位不守恒（差 " + abs(exact.subtract(calc.netPayout)) + "），对账不平") + "\n");
        }

        // N10 「费用子限额 = 0」的语义：无此项限额 or 限额为零？
        {
            Ports.InMemoryPorts p = ports();
            SettlementEngine e = engine(p);
            Map<String, FormulaSnapshot.FeeConfig> m = new LinkedHashMap<>();
            // 产品未配置费用子限额（业界惯例：0 = 不限制），比例 80%
            m.put("合理金额", new FormulaSnapshot.FeeConfig("合理金额", Money.of(80),
                    Money.ZERO, Money.ZERO, Money.ZERO, null));
            FormulaSnapshot snap = reimb(m, BIG);
            IndemnityCalculation c = e.settle(ctx("C-SUB0", "POL-I", Money.of(1000), BIG, Money.ZERO, Money.ZERO), snap);
            System.out.println("N10 费用子限额 = 0 的语义（产品未配置子限额，费用 1000 / 80%）");
            System.out.println("   实测: 净付=" + c.netPayout);
            System.out.println("   正解: 800.00（0 = 无此项限额；tpa-saas 与主流产品配置惯例）");
            System.out.println("   判定: " + (c.netPayout.cmp(Money.of(800)) == 0 ? "OK"
                    : "❌ 缺陷 — 0 被当成「限额为零」全额拒付；且现有 20 个场景用例因全部传入大额子限额而掩盖了它（典型假绿）") + "\n");
        }

        System.out.println("===== 诊断结束 =====");
    }
}
