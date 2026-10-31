package com.bone.synap.settlement.probe;

import com.bone.synap.settlement.Money;
import com.bone.synap.settlement.domain.AdjustmentLine;
import com.bone.synap.settlement.domain.ClaimContext;
import com.bone.synap.settlement.domain.FormulaSnapshot;
import com.bone.synap.settlement.domain.IndemnityCalculation;
import com.bone.synap.settlement.engine.EngineFlags;
import com.bone.synap.settlement.engine.SettlementEngine;
import com.bone.synap.settlement.port.Ports;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * 理算引擎探针套件（防假绿核心）。
 *
 * 设计原则（对应《理算引擎内部设计》§5）：
 *   每条探针断言一个「性质」，而非一个巧合结果；并验证「删掉实现哪一行会红」——
 *   通过对比「正确引擎全绿」与「变异引擎必红」来证明探针有判别力。
 *
 * 第一轮变异（10）：noMin / livePlan / ignoreConfirm / noTrace / noAccum / noIdem /
 *                   noTenantScope / noIndemnity / noRatioClamp / dedAfterRatio
 * 第二轮变异（9） ：noLiabAccum / noReversal / noPreAuthFrozen / totalLiabInPreAuth /
 *                   authWhenAllRejected / rejectDeductPollution / noCAS /
 *                   noApportion / zeroIsLimit
 * 第二轮变异全部源自「先打印实测值再下结论」的诊断（N1–N10），
 * 其中 N10 尤其关键：它揭示现有 20 个场景用例因测试数据恰好都设了大额子限额而掩盖了
 * 「限额 0 = 全额拒付」这一静默少赔缺陷 —— 典型假绿。
 */
public final class ProbeSuite {

    public static void main(String[] args) {
        RunResult cr = run("CORRECT", f -> { });
        print(cr);
        List<RunResult> mutants = new ArrayList<>();
        mutants.add(run("noMin",                f -> f.enforceLimitMin = false));
        mutants.add(run("livePlan",             f -> f.freezeSnapshot = false));
        mutants.add(run("ignoreConfirm",        f -> f.requireConfirm = false));
        mutants.add(run("noTrace",              f -> f.recordTrace = false));
        mutants.add(run("noAccum",              f -> f.applyAccumulation = false));
        mutants.add(run("noIdem",               f -> f.applyIdempotency = false));
        mutants.add(run("noTenantScope",        f -> f.tenantScopedKeys = false));
        mutants.add(run("noIndemnity",          f -> f.applyIndemnityPrinciple = false));
        mutants.add(run("noRatioClamp",         f -> f.clampRatioAt100 = false));
        mutants.add(run("dedAfterRatio",        f -> f.deductibleBeforeRatio = false));
        mutants.add(run("noLiabAccum",          f -> f.applyLiabilityAccumulation = false));
        mutants.add(run("noReversal",           f -> f.applyReversal = false));
        mutants.add(run("noPreAuthFrozen",      f -> f.applyPreAuthFrozen = false));
        mutants.add(run("totalLiabInPreAuth",   f -> f.useRemainingLiability = false));
        mutants.add(run("authWhenAllRejected",  f -> f.zeroAuthWhenAllRejected = false));
        mutants.add(run("rejectDeductPollution",f -> f.rejectRowZeroDeduct = false));
        mutants.add(run("noCAS",                f -> f.applyIdempotencyCAS = false));
        mutants.add(run("noApportion",          f -> f.apportionRounding = false));
        mutants.add(run("zeroIsLimit",          f -> f.zeroMeansUnlimited = false));
        mutants.forEach(ProbeSuite::print);

        Summary s = runAll();
        System.out.println();
        System.out.println("===== 防假绿判定 =====");
        System.out.println("CORRECT 全绿        : " + s.correctAllGreen + "（" + s.totalProbes + " 条探针）");
        System.out.println("变异被捕获           : " + s.mutantsCaptured + "/" + s.mutantsTotal);
        if (!s.escaped.isEmpty()) System.out.println("逃逸变异（假绿风险） : " + s.escaped);

        if (!s.correctAllGreen) { System.err.println("!! 正确引擎未通过门禁（假红，需修复实现）"); System.exit(1); }
        if (!s.escaped.isEmpty()) { System.err.println("!! 存在未被捕获的变异（探针失辨）"); System.exit(2); }
        System.out.println("ALL GREEN: 正确引擎全绿，且 " + s.mutantsTotal + " 类变异均被探针捕获。");
    }

    static final class RunResult {
        final String tag;
        final Map<String, Boolean> results;
        RunResult(String tag, Map<String, Boolean> results) { this.tag = tag; this.results = results; }
    }

    /** 防假绿汇总（供 JUnit 断言，不调用 System.exit）。 */
    public static final class Summary {
        public final boolean correctAllGreen;
        public final int totalProbes;
        public final int mutantsTotal;
        public final int mutantsCaptured;
        public final List<String> escaped;
        Summary(boolean correctAllGreen, int totalProbes, int mutantsTotal, int mutantsCaptured, List<String> escaped) {
            this.correctAllGreen = correctAllGreen;
            this.totalProbes = totalProbes;
            this.mutantsTotal = mutantsTotal;
            this.mutantsCaptured = mutantsCaptured;
            this.escaped = escaped;
        }
    }

    /** 运行「正确引擎 + 19 类变异」，返回结构化汇总（不退出 JVM，供测试框架调用）。 */
    public static Summary runAll() {
        RunResult cr = run("CORRECT", f -> { });
        List<RunResult> mutants = new ArrayList<>();
        mutants.add(run("noMin",                f -> f.enforceLimitMin = false));
        mutants.add(run("livePlan",             f -> f.freezeSnapshot = false));
        mutants.add(run("ignoreConfirm",        f -> f.requireConfirm = false));
        mutants.add(run("noTrace",              f -> f.recordTrace = false));
        mutants.add(run("noAccum",              f -> f.applyAccumulation = false));
        mutants.add(run("noIdem",               f -> f.applyIdempotency = false));
        mutants.add(run("noTenantScope",        f -> f.tenantScopedKeys = false));
        mutants.add(run("noIndemnity",          f -> f.applyIndemnityPrinciple = false));
        mutants.add(run("noRatioClamp",         f -> f.clampRatioAt100 = false));
        mutants.add(run("dedAfterRatio",        f -> f.deductibleBeforeRatio = false));
        mutants.add(run("noLiabAccum",          f -> f.applyLiabilityAccumulation = false));
        mutants.add(run("noReversal",           f -> f.applyReversal = false));
        mutants.add(run("noPreAuthFrozen",      f -> f.applyPreAuthFrozen = false));
        mutants.add(run("totalLiabInPreAuth",   f -> f.useRemainingLiability = false));
        mutants.add(run("authWhenAllRejected",  f -> f.zeroAuthWhenAllRejected = false));
        mutants.add(run("rejectDeductPollution",f -> f.rejectRowZeroDeduct = false));
        mutants.add(run("noCAS",                f -> f.applyIdempotencyCAS = false));
        mutants.add(run("noApportion",          f -> f.apportionRounding = false));
        mutants.add(run("zeroIsLimit",          f -> f.zeroMeansUnlimited = false));

        boolean correctAll = cr.results.values().stream().allMatch(Boolean::booleanValue);
        List<String> escaped = new ArrayList<>();
        for (RunResult m : mutants) {
            if (m.results.values().stream().allMatch(Boolean::booleanValue)) escaped.add(m.tag);
        }
        return new Summary(correctAll, cr.results.size(), mutants.size(),
                mutants.size() - escaped.size(), escaped);
    }

    static void print(RunResult rr) {
        List<String> red = new ArrayList<>();
        rr.results.forEach((k, v) -> { if (!v) red.add(k); });
        System.out.println("---- " + rr.tag + " ---- " + (red.isEmpty() ? "ALL PASS" : "RED: " + red));
    }

    /** 每次运行构建全新的 ports + 引擎，避免跨变异状态污染（尤其跨案累计与幂等存储）。 */
    static RunResult run(String tag, Consumer<EngineFlags> mutator) {
        Ports.InMemoryPorts ports = new Ports.InMemoryPorts();
        EngineFlags flags = EngineFlags.allOn();
        mutator.accept(flags);
        SettlementEngine e = new SettlementEngine(ports, ports, ports, ports, ports, ports, ports, flags);

        Map<String, Boolean> r = new LinkedHashMap<>();

        // P1 集成：引擎实际被调用，产出 2 行且合计 > 0
        {
            FormulaSnapshot snap = reimbSnap(500);
            IndemnityCalculation calc = e.settle(baseCtx(List.of()), snap);
            r.put("P1_integration", calc != null && calc.lines.size() == 2 && calc.totalPayout.cmp(Money.ZERO) > 0);
        }

        // P4 控额 min：责任控额 500 -> 合计被截断为 500
        {
            FormulaSnapshot snap = reimbSnap(500);
            IndemnityCalculation calc = e.settle(baseCtx(List.of()), snap);
            r.put("P4_limitMin", calc.totalPayout.cmp(Money.of(500)) == 0);
        }

        // P3 负值钳零
        {
            FormulaSnapshot snap = reimbSnap(1_000_000);
            IndemnityCalculation calc = e.settle(negativeCtx(), snap);
            AdjustmentLine line = find(calc, "合理金额");
            r.put("P3_negativeClamp", line != null && line.feePayout.isZero() && !line.feePayout.isNegative());
        }

        // P3b 免赔额大于费用额：不得产出负赔付
        {
            Map<String, FormulaSnapshot.FeeConfig> m = new LinkedHashMap<>();
            m.put("合理金额", new FormulaSnapshot.FeeConfig("合理金额", Money.of(80), Money.of(1_000_000), Money.of(5000), Money.of(0)));
            FormulaSnapshot snap = new FormulaSnapshot("L1", "报销", FormulaSnapshot.LiabilityType.REIMBURSEMENT,
                    Money.of(1_000_000), Money.ZERO, Money.ZERO, Money.ZERO, 0, m, "v1", 1L);
            Map<String, Money> f = new LinkedHashMap<>();
            f.put("合理金额", Money.of(1000));
            ClaimContext ctx = new ClaimContext("C-B", "T1", "P1", "POL-B", "2026", f,
                    Money.of(1_000_000), Money.ZERO, Money.ZERO, List.of());
            IndemnityCalculation c = e.settle(ctx, snap);
            r.put("P3b_deductibleExceedsFee", c.netPayout.isZero() && !c.netPayout.isNegative());
        }

        // P2 快照复算
        {
            FormulaSnapshot frozen = reimbSnap(1_000_000);
            Money t1 = e.settle(baseCtx(List.of()), frozen).totalPayout;
            e.livePlan = reimbSnapRatio(1_000_000, 100);
            Money t2 = e.settle(baseCtx(List.of()), frozen).totalPayout;
            e.livePlan = null;
            r.put("P2_snapshotFreeze", t1.cmp(t2) == 0);
        }

        // P5 AI 护栏
        {
            FormulaSnapshot snap = reimbSnap(1_000_000);
            ClaimContext unconf = baseCtx(List.of(new ClaimContext.AiSuggestion("合理金额", true, false)));
            ClaimContext conf   = baseCtx(List.of(new ClaimContext.AiSuggestion("合理金额", true, true)));
            Money tU = e.settle(unconf, snap).totalPayout;
            Money tC = e.settle(conf, snap).totalPayout;
            r.put("P5_noBonusWhenUnconfirmed", tU.cmp(Money.of(900)) == 0);
            r.put("P5_bonusWhenConfirmed",     tC.cmp(Money.of(1000)) == 0);
        }

        // P6 端口隔离
        {
            r.put("P6_portIsolation", ports.reserveCalls > 0 && ports.publishCalls > 0 && ports.traceCalls > 0);
        }

        // P7 占用不变式
        {
            FormulaSnapshot snap = reimbSnap(1_000_000);
            IndemnityCalculation calc = e.settle(baseCtx(List.of()), snap);
            boolean ok = true;
            for (AdjustmentLine l : calc.lines) {
                Money prod = l.feeOccupy.multiply(l.ratioPayout.ratio()).rounded();
                if (l.feePayout.cmp(prod) != 0) ok = false;
                if (l.feeOccupy.isNegative()) ok = false;
            }
            r.put("P7_occupyInvariant", ok);
        }

        // P8 归因完备
        {
            FormulaSnapshot snap = reimbSnap(1_000_000);
            IndemnityCalculation calc = e.settle(baseCtx(List.of()), snap);
            Money sum = Money.ZERO;
            for (AdjustmentLine l : calc.lines) sum = sum.add(l.feePayout);
            r.put("P8_attribution", calc.totalPayout.cmp(sum) == 0);
        }

        // P9 规则溯源
        {
            FormulaSnapshot snap = reimbSnap(1_000_000);
            IndemnityCalculation calc = e.settle(baseCtx(List.of()), snap);
            List<String> rules = calc.ruleTrace.getOrDefault("合理金额", List.of());
            r.put("P9_explainability", rules.contains("R-RAT-01")
                    && calc.explain.contains("合理金额") && calc.explain.contains("800"));
        }

        // P10 跨案累计（年度免赔作用于费用基数）
        {
            String pol = "POL-DED";
            FormulaSnapshot snap = reimbSnap(1_000_000);
            IndemnityCalculation c1 = e.settle(accumCtx(pol, Money.of(1000), Money.ZERO, "C-D1"), snap);
            IndemnityCalculation c2 = e.settle(accumCtx(pol, Money.of(1000), Money.ZERO, "C-D2"), snap);
            r.put("P10_accumDeductible", c1.netPayout.cmp(Money.of(150)) == 0 && c2.netPayout.cmp(Money.of(900)) == 0);
        }

        // P11 跨案累计（年度限额封顶）
        {
            String pol = "POL-LIM";
            FormulaSnapshot snap = reimbSnap(1_000_000);
            IndemnityCalculation c1 = e.settle(accumCtx(pol, Money.ZERO, Money.of(1000), "C-L1"), snap);
            IndemnityCalculation c2 = e.settle(accumCtx(pol, Money.ZERO, Money.of(1000), "C-L2"), snap);
            r.put("P11_accumLimit", c1.netPayout.cmp(Money.of(900)) == 0 && c2.netPayout.cmp(Money.of(100)) == 0);
        }

        // P12 幂等
        {
            String pol = "POL-IDEM";
            FormulaSnapshot snap = reimbSnap(1_000_000);
            ClaimContext ctx = accumCtx(pol, Money.of(1000), Money.ZERO, "C-IDEM");
            IndemnityCalculation c1 = e.settle(ctx, snap);
            IndemnityCalculation c2 = e.settle(ctx, snap);
            r.put("P12_idempotency", c1.netPayout.cmp(Money.of(150)) == 0 && c2.netPayout.cmp(c1.netPayout) == 0);
        }

        // P13 租户隔离
        {
            FormulaSnapshot snap = reimbSnapRatio(1_000_000, 100);
            Map<String, Money> f = new LinkedHashMap<>();
            f.put("合理金额", Money.of(1000));
            ClaimContext t1 = new ClaimContext("C-T1", "T1", "P1", "POL-SAME", "2026", f,
                    Money.of(1_000_000), Money.ZERO, Money.of(1000), List.of());
            ClaimContext t2 = new ClaimContext("C-T2", "T2", "P1", "POL-SAME", "2026", f,
                    Money.of(1_000_000), Money.ZERO, Money.of(1000), List.of());
            IndemnityCalculation c1 = e.settle(t1, snap);
            IndemnityCalculation c2 = e.settle(t2, snap);
            r.put("P13_tenantIsolation", c1.netPayout.cmp(Money.of(1000)) == 0 && c2.netPayout.cmp(Money.of(1000)) == 0);
        }

        // P14 不可获利
        {
            FormulaSnapshot snap = reimbSnap(1_000_000);
            Map<String, Money> f = new LinkedHashMap<>();
            f.put("合理金额", Money.of(10000));
            ClaimContext ctx = new ClaimContext("C-IND", "T1", "P1", "POL-IND", "2026", f,
                    Money.of(1_000_000), Money.ZERO, Money.ZERO, List.of(), List.of(), Money.of(5000), false);
            IndemnityCalculation c = e.settle(ctx, snap);
            r.put("P14_indemnityPrinciple", c.netPayout.cmp(Money.of(4000)) == 0
                    && c.netPayout.add(Money.of(5000)).cmp(Money.of(10000)) <= 0);
        }

        // P15 比例硬护栏
        {
            Map<String, FormulaSnapshot.FeeConfig> m = new LinkedHashMap<>();
            m.put("合理金额", new FormulaSnapshot.FeeConfig("合理金额", Money.of(95), Money.of(1_000_000), Money.of(0), Money.of(0)));
            FormulaSnapshot snap = new FormulaSnapshot("L1", "报销", FormulaSnapshot.LiabilityType.REIMBURSEMENT,
                    Money.of(1_000_000), Money.ZERO, Money.ZERO, Money.ZERO, 0, m, "v1", 1L);
            Map<String, Money> f = new LinkedHashMap<>();
            f.put("合理金额", Money.of(1000));
            ClaimContext ctx = new ClaimContext("C-RATIO", "T1", "P1", "POL-RATIO", "2026", f,
                    Money.of(1_000_000), Money.ZERO, Money.ZERO,
                    List.of(new ClaimContext.AiSuggestion("合理金额", true, true)));
            r.put("P15_ratioClamp", e.settle(ctx, snap).netPayout.cmp(Money.of(1000)) == 0);
        }

        // P16 免赔额顺序
        {
            FormulaSnapshot snap = reimbSnap(1_000_000);
            Map<String, Money> f = new LinkedHashMap<>();
            f.put("合理金额", Money.of(20000));
            ClaimContext ctx = new ClaimContext("C-ORD", "T1", "P1", "POL-ORD", "2026", f,
                    Money.of(1_000_000), Money.of(10000), Money.ZERO, List.of());
            r.put("P16_deductibleBeforeRatio", e.settle(ctx, snap).netPayout.cmp(Money.of(8000)) == 0);
        }

        // P17 风控 P0 fail-closed
        {
            Ports.InMemoryPorts p2 = new Ports.InMemoryPorts();
            SettlementEngine e2 = new SettlementEngine(p2, p2, p2, p2, p2, p2, p2, flags);
            Map<String, Money> f = new LinkedHashMap<>();
            f.put("合理金额", Money.of(1000));
            ClaimContext ctx = new ClaimContext("C-FRAUD", "T1", "P1", "POL-F", "2026", f,
                    Money.of(1_000_000), Money.ZERO, Money.ZERO, List.of(), List.of(), Money.ZERO, true);
            IndemnityCalculation c = e2.settle(ctx, reimbSnap(1_000_000));
            r.put("P17_fraudHold", c.status == IndemnityCalculation.SettlementStatus.HOLD
                    && c.netPayout.isZero() && p2.reserveCalls == 0);
        }

        // P18 累计并发：乐观锁冲突必须 CONFLICT + 引擎 fail-closed 抛异常
        {
            Ports.InMemoryPorts p3 = new Ports.InMemoryPorts();
            Ports.AccUsage u0 = p3.getUsage("T1@POL-CONF", "2026");
            Ports.CommitResult first = p3.commitUsage("T1@POL-CONF", "2026", "C-X1", Money.of(100), Money.ZERO, Money.ZERO, u0.version);
            Ports.CommitResult stale = p3.commitUsage("T1@POL-CONF", "2026", "C-X2", Money.of(100), Money.ZERO, Money.ZERO, u0.version);
            Ports.CommitResult dup   = p3.commitUsage("T1@POL-CONF", "2026", "C-X1", Money.of(100), Money.ZERO, Money.ZERO, u0.version + 1);

            Ports.AccumulationPort conflictPort = new Ports.AccumulationPort() {
                @Override public Ports.AccUsage getUsage(String k, String period) { return Ports.AccUsage.empty(); }
                @Override public Ports.CommitResult commitUsage(String k, String period, String claimId,
                                                               Money d, Money l, Money li, long v) {
                    return Ports.CommitResult.CONFLICT;
                }
                @Override public void reverse(String k, String period, String claimId, Money d, Money l, Money li) { }
                @Override public Ports.CommitResult commitFrozen(String k, String period, String authId, Money amount) {
                    return Ports.CommitResult.COMMITTED;
                }
                @Override public void releaseFrozen(String k, String period, String authId) { }
            };
            boolean throwsOnConflict;
            try {
                Ports.InMemoryPorts p4 = new Ports.InMemoryPorts();
                SettlementEngine e4 = new SettlementEngine(p4, p4, p4, p4, conflictPort, p4, p4, flags);
                Map<String, Money> f = new LinkedHashMap<>();
                f.put("合理金额", Money.of(1000));
                e4.settle(new ClaimContext("C-CONF", "T1", "P1", "POL-CONF", "2026", f,
                        Money.of(1_000_000), Money.of(1000), Money.ZERO, List.of()), reimbSnap(1_000_000));
                throwsOnConflict = false;
            } catch (SettlementEngine.ConcurrentAccumulationException ex) {
                throwsOnConflict = true;
            }
            r.put("P18_accumulationConflict", first == Ports.CommitResult.COMMITTED
                    && stale == Ports.CommitResult.CONFLICT
                    && dup == Ports.CommitResult.DUPLICATE
                    && throwsOnConflict);
        }

        // ================= 第二轮新增探针（N1–N10） =================

        // P19 责任限额跨案累计（N1）：责任年限额 5000，首案 4000，次案仅余 1000
        {
            Ports.InMemoryPorts p = new Ports.InMemoryPorts();
            SettlementEngine e2 = new SettlementEngine(p, p, p, p, p, p, p, flags);
            Map<String, FormulaSnapshot.FeeConfig> m = new LinkedHashMap<>();
            m.put("合理金额", new FormulaSnapshot.FeeConfig("合理金额", Money.of(100), Money.of(1_000_000), Money.ZERO, Money.ZERO, null));
            FormulaSnapshot snap = new FormulaSnapshot("L1", "报销", FormulaSnapshot.LiabilityType.REIMBURSEMENT,
                    Money.of(5000), Money.ZERO, Money.ZERO, Money.ZERO, 0, m, "v1", 1L);
            Map<String, Money> f = new LinkedHashMap<>();
            f.put("合理金额", Money.of(4000));
            ClaimContext c1 = new ClaimContext("C-LA1", "T1", "P1", "POL-LA", "2026", f,
                    Money.of(1_000_000), Money.ZERO, Money.ZERO, List.of());
            ClaimContext c2 = new ClaimContext("C-LA2", "T1", "P1", "POL-LA", "2026", f,
                    Money.of(1_000_000), Money.ZERO, Money.ZERO, List.of());
            IndemnityCalculation r1 = e2.settle(c1, snap);
            IndemnityCalculation r2 = e2.settle(c2, snap);
            r.put("P19_liabilityAccumulation",
                    r1.netPayout.cmp(Money.of(4000)) == 0 && r2.netPayout.cmp(Money.of(1000)) == 0);
        }

        // P20 重算冲正（N2）：补交发票 6000→8000，累计必须等于最新结论
        {
            Ports.InMemoryPorts p = new Ports.InMemoryPorts();
            SettlementEngine e2 = new SettlementEngine(p, p, p, p, p, p, p, flags);
            Map<String, FormulaSnapshot.FeeConfig> m = new LinkedHashMap<>();
            m.put("合理金额", new FormulaSnapshot.FeeConfig("合理金额", Money.of(100), Money.of(1_000_000), Money.ZERO, Money.ZERO, null));
            FormulaSnapshot snap = new FormulaSnapshot("L1", "报销", FormulaSnapshot.LiabilityType.REIMBURSEMENT,
                    Money.of(1_000_000), Money.ZERO, Money.ZERO, Money.ZERO, 0, m, "v1", 1L);
            Map<String, Money> f1 = new LinkedHashMap<>(); f1.put("合理金额", Money.of(6000));
            Map<String, Money> f2 = new LinkedHashMap<>(); f2.put("合理金额", Money.of(8000));
            ClaimContext ctx1 = new ClaimContext("C-RV", "T1", "P1", "POL-RV", "2026", f1,
                    Money.of(1_000_000), Money.ZERO, Money.of(10000), List.of());
            ClaimContext ctx2 = new ClaimContext("C-RV", "T1", "P1", "POL-RV", "2026", f2,
                    Money.of(1_000_000), Money.ZERO, Money.of(10000), List.of());
            IndemnityCalculation c1 = e2.settle(ctx1, snap);
            IndemnityCalculation c2 = e2.settle(ctx2, snap);
            Ports.AccUsage u = p.getUsage("T1@POL-RV", "2026");
            r.put("P20_recalculationReversal",
                    c2.netPayout.cmp(Money.of(8000)) == 0 && u.limitUsed.cmp(Money.of(8000)) == 0);
        }

        // P21 预授权计入冻结额度（N3）：年限额 10000，3 次各预估 6000，累计授权 ≤ 10000
        {
            Ports.InMemoryPorts p = new Ports.InMemoryPorts();
            SettlementEngine e2 = new SettlementEngine(p, p, p, p, p, p, p, flags);
            Map<String, FormulaSnapshot.FeeConfig> m = new LinkedHashMap<>();
            m.put("合理金额", new FormulaSnapshot.FeeConfig("合理金额", Money.of(100), Money.of(1_000_000), Money.ZERO, Money.ZERO, null));
            FormulaSnapshot snap = new FormulaSnapshot("L1", "报销", FormulaSnapshot.LiabilityType.REIMBURSEMENT,
                    Money.of(1_000_000), Money.ZERO, Money.ZERO, Money.ZERO, 0, m, "v1", 1L);
            Money total = Money.ZERO;
            for (int i = 1; i <= 3; i++) {
                ClaimContext c = new ClaimContext("C-PA" + i, "T1", "P1", "POL-PA", "2026",
                        new LinkedHashMap<>(), Money.of(1_000_000), Money.ZERO, Money.of(10000), List.of());
                total = total.add(e2.preAuthorizeAndHold(c, snap, Money.of(6000), 60_000L).authorizedAmount);
            }
            r.put("P21_preAuthFrozen", total.cmp(Money.of(10000)) <= 0);
        }

        // P22 预授权用剩余责任限额（N4）：责任 5000 已用 3000 → 授权 ≤ 2000
        {
            Ports.InMemoryPorts p = new Ports.InMemoryPorts();
            SettlementEngine e2 = new SettlementEngine(p, p, p, p, p, p, p, flags);
            Map<String, FormulaSnapshot.FeeConfig> m = new LinkedHashMap<>();
            m.put("合理金额", new FormulaSnapshot.FeeConfig("合理金额", Money.of(100), Money.of(1_000_000), Money.ZERO, Money.ZERO, null));
            FormulaSnapshot snap = new FormulaSnapshot("L1", "报销", FormulaSnapshot.LiabilityType.REIMBURSEMENT,
                    Money.of(5000), Money.ZERO, Money.ZERO, Money.ZERO, 0, m, "v1", 1L);
            Map<String, Money> fx = new LinkedHashMap<>(); fx.put("合理金额", Money.of(3000));
            e2.settle(new ClaimContext("C-USED", "T1", "P1", "POL-UL", "2026", fx,
                    Money.of(1_000_000), Money.ZERO, Money.of(10000), List.of()), snap);
            ClaimContext c = new ClaimContext("C-PA2", "T1", "P1", "POL-UL", "2026",
                    new LinkedHashMap<>(), Money.of(1_000_000), Money.ZERO, Money.of(10000), List.of());
            Money auth = e2.preAuthorize(c, snap, Money.of(10000)).authorizedAmount;
            r.put("P22_preAuthRemainingLiability", auth.cmp(Money.of(2000)) <= 0);
        }

        // P23 无可赔费用时授权 0（N5）：全部丙类 → 授权 0
        {
            Ports.InMemoryPorts p = new Ports.InMemoryPorts();
            SettlementEngine e2 = new SettlementEngine(p, p, p, p, p, p, p, flags);
            Map<String, FormulaSnapshot.FeeConfig> m = new LinkedHashMap<>();
            m.put("丙类", new FormulaSnapshot.FeeConfig("丙类", Money.of(100), Money.of(1_000_000), Money.ZERO, Money.ZERO, "OUT_CATALOG"));
            FormulaSnapshot snap = new FormulaSnapshot("L1", "报销", FormulaSnapshot.LiabilityType.REIMBURSEMENT,
                    Money.of(1_000_000), Money.ZERO, Money.ZERO, Money.ZERO, 0, m, "v1", 1L);
            ClaimContext c = new ClaimContext("C-PB", "T1", "P1", "POL-PB", "2026",
                    new LinkedHashMap<>(), Money.of(1_000_000), Money.ZERO, Money.of(10000), List.of());
            r.put("P23_zeroAuthWhenAllRejected", e2.preAuthorize(c, snap, Money.of(8000)).authorizedAmount.isZero());
        }

        // P24 拒赔行免赔额记 0（N6）
        {
            Ports.InMemoryPorts p = new Ports.InMemoryPorts();
            SettlementEngine e2 = new SettlementEngine(p, p, p, p, p, p, p, flags);
            Map<String, FormulaSnapshot.FeeConfig> m = new LinkedHashMap<>();
            m.put("合理金额", new FormulaSnapshot.FeeConfig("合理金额", Money.of(100), Money.of(1_000_000), Money.ZERO, Money.ZERO, null));
            m.put("丙类", new FormulaSnapshot.FeeConfig("丙类", Money.of(100), Money.of(1_000_000), Money.ZERO, Money.ZERO, "OUT_CATALOG"));
            FormulaSnapshot snap = new FormulaSnapshot("L1", "报销", FormulaSnapshot.LiabilityType.REIMBURSEMENT,
                    Money.of(1_000_000), Money.ZERO, Money.ZERO, Money.ZERO, 0, m, "v1", 1L);
            Map<String, Money> f = new LinkedHashMap<>();
            f.put("合理金额", Money.of(1000)); f.put("丙类", Money.of(500));
            IndemnityCalculation c = e2.settle(new ClaimContext("C-RJD", "T1", "P1", "POL-RJD", "2026", f,
                    Money.of(1_000_000), Money.ZERO, Money.ZERO, List.of()), snap);
            AdjustmentLine rj = find(c, "丙类");
            r.put("P24_rejectRowZeroDeduct", rj != null && rj.rejectType != null && rj.feeDeduct.isZero());
        }

        // P25 幂等 CAS 租约（N7）：租约已被占用时，并发提交必须 fail-closed
        {
            Ports.InMemoryPorts p = new Ports.InMemoryPorts();
            SettlementEngine e2 = new SettlementEngine(p, p, p, p, p, p, p, flags);
            FormulaSnapshot snap = reimbSnap(1_000_000);
            Map<String, Money> f = new LinkedHashMap<>(); f.put("合理金额", Money.of(1000));
            ClaimContext ctx = new ClaimContext("C-CAS", "T1", "P1", "POL-CAS", "2026", f,
                    Money.of(1_000_000), Money.of(1000), Money.ZERO, List.of());
            String idemKey = ctx.claimId + "|" + snap.formulaHash() + "|" + ctx.inputHash();
            p.tryBegin(idemKey); // 模拟另一在途请求已取得租约
            boolean threw = false;
            try { e2.settle(ctx, snap); } catch (SettlementEngine.ConcurrentSettlementException ex) { threw = true; }
            r.put("P25_idempotencyCAS", threw);
        }

        // P26 冲正通道（N8）：撤件/改判可回退累计占用
        {
            Ports.InMemoryPorts p = new Ports.InMemoryPorts();
            SettlementEngine e2 = new SettlementEngine(p, p, p, p, p, p, p, flags);
            FormulaSnapshot snap = reimbSnapRatio(1_000_000, 100);
            Map<String, Money> f = new LinkedHashMap<>(); f.put("合理金额", Money.of(3000));
            IndemnityCalculation c = e2.settle(new ClaimContext("C-REV", "T1", "P1", "POL-REV", "2026", f,
                    Money.of(1_000_000), Money.ZERO, Money.of(10000), List.of()), snap);
            Money before = p.getUsage("T1@POL-REV", "2026").limitUsed;
            e2.reverseSettlement(c);
            Money after = p.getUsage("T1@POL-REV", "2026").limitUsed;
            r.put("P26_reversal", before.cmp(Money.of(3000)) == 0 && after.isZero());
        }

        // P27 分位守恒（N9）：逐行取整合计 == 总额口径取整
        {
            Ports.InMemoryPorts p = new Ports.InMemoryPorts();
            SettlementEngine e2 = new SettlementEngine(p, p, p, p, p, p, p, flags);
            Money r333 = Money.of("33.333333");
            Map<String, FormulaSnapshot.FeeConfig> m = new LinkedHashMap<>();
            m.put("A", new FormulaSnapshot.FeeConfig("A", r333, Money.of(1_000_000), Money.ZERO, Money.ZERO, null));
            m.put("B", new FormulaSnapshot.FeeConfig("B", r333, Money.of(1_000_000), Money.ZERO, Money.ZERO, null));
            m.put("C", new FormulaSnapshot.FeeConfig("C", r333, Money.of(1_000_000), Money.ZERO, Money.ZERO, null));
            FormulaSnapshot snap = new FormulaSnapshot("L1", "报销", FormulaSnapshot.LiabilityType.REIMBURSEMENT,
                    Money.of(1_000_000), Money.ZERO, Money.ZERO, Money.ZERO, 0, m, "v1", 1L);
            Map<String, Money> f = new LinkedHashMap<>();
            f.put("A", Money.of(1000)); f.put("B", Money.of(1000)); f.put("C", Money.of(1000));
            IndemnityCalculation c = e2.settle(new ClaimContext("C-RND", "T1", "P1", "POL-RND", "2026", f,
                    Money.of(1_000_000), Money.ZERO, Money.ZERO, List.of()), snap);
            Money exact = Money.of(3000).multiply(r333.ratio()).rounded();
            r.put("P27_roundingApportionment", c.netPayout.cmp(exact) == 0);
        }

        // P28 限额 0 = 未配置（N10）：未配费用子限额不得全额拒付
        {
            Ports.InMemoryPorts p = new Ports.InMemoryPorts();
            SettlementEngine e2 = new SettlementEngine(p, p, p, p, p, p, p, flags);
            Map<String, FormulaSnapshot.FeeConfig> m = new LinkedHashMap<>();
            m.put("合理金额", new FormulaSnapshot.FeeConfig("合理金额", Money.of(80), Money.ZERO, Money.ZERO, Money.ZERO, null));
            FormulaSnapshot snap = new FormulaSnapshot("L1", "报销", FormulaSnapshot.LiabilityType.REIMBURSEMENT,
                    Money.of(1_000_000), Money.ZERO, Money.ZERO, Money.ZERO, 0, m, "v1", 1L);
            Map<String, Money> f = new LinkedHashMap<>(); f.put("合理金额", Money.of(1000));
            IndemnityCalculation c = e2.settle(new ClaimContext("C-SUB0", "T1", "P1", "POL-SUB0", "2026", f,
                    Money.of(1_000_000), Money.ZERO, Money.ZERO, List.of()), snap);
            r.put("P28_zeroMeansUnlimited", c.netPayout.cmp(Money.of(800)) == 0);
        }

        return new RunResult(tag, r);
    }

    // ---- 场景构造 ----

    static FormulaSnapshot reimbSnap(long limit) { return reimbSnapRatio(limit, 80); }

    static FormulaSnapshot reimbSnapRatio(long limit, int ratioFor合理金额) {
        Map<String, FormulaSnapshot.FeeConfig> m = new LinkedHashMap<>();
        m.put("合理金额", new FormulaSnapshot.FeeConfig("合理金额", Money.of(ratioFor合理金额), Money.of(1_000_000), Money.of(0), Money.of(0)));
        m.put("自付二",   new FormulaSnapshot.FeeConfig("自付二",   Money.of(50),            Money.of(1_000_000), Money.of(0), Money.of(0)));
        return new FormulaSnapshot("L1", "报销型责任", FormulaSnapshot.LiabilityType.REIMBURSEMENT,
                Money.of(limit), Money.ZERO, Money.ZERO, Money.ZERO, 0, m, "v1", 1L);
    }

    static ClaimContext baseCtx(List<ClaimContext.AiSuggestion> suggestions) {
        Map<String, Money> f = new LinkedHashMap<>();
        f.put("合理金额", Money.of(1000));
        f.put("自付二",   Money.of(200));
        return new ClaimContext("C1", "T1", "P1", "POL1", "2026", f, Money.of(1_000_000),
                Money.ZERO, Money.ZERO, suggestions);
    }

    static ClaimContext negativeCtx() {
        Map<String, Money> f = new LinkedHashMap<>();
        f.put("合理金额", Money.of(-500));
        f.put("自付二",   Money.of(200));
        return new ClaimContext("C1", "T1", "P1", "POL1", "2026", f, Money.of(1_000_000),
                Money.ZERO, Money.ZERO, List.of());
    }

    static ClaimContext accumCtx(String policyId, Money annualDeductible, Money annualLimit, String claimId) {
        Map<String, Money> f = new LinkedHashMap<>();
        f.put("合理金额", Money.of(1000));
        f.put("自付二",   Money.of(200));
        return new ClaimContext(claimId, "T1", "P1", policyId, "2026", f, Money.of(1_000_000),
                annualDeductible, annualLimit, List.of());
    }

    static AdjustmentLine find(IndemnityCalculation calc, String feeType) {
        for (AdjustmentLine l : calc.lines) if (l.feeType.equals(feeType)) return l;
        return null;
    }
}
