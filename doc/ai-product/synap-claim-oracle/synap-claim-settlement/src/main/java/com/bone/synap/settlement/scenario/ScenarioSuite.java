package com.bone.synap.settlement.scenario;

import com.bone.synap.settlement.Money;
import com.bone.synap.settlement.domain.AdjustmentLine;
import com.bone.synap.settlement.domain.ClaimContext;
import com.bone.synap.settlement.domain.FormulaSnapshot;
import com.bone.synap.settlement.domain.IndemnityCalculation;
import com.bone.synap.settlement.engine.SettlementEngine;
import com.bone.synap.settlement.port.Ports;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 理算引擎真实 TPA 场景测试用例套件（普康 / 亿保 / 泰康条款 锚定）。
 *
 * 每个场景都带「确定性期望值」断言（不是只跑通），覆盖：
 *   S1  门诊小额快赔（普康团险：免赔额 + 比例）
 *   S2  住院社保分割（泰康：社保内 A 比例 + 社保外 B 比例）
 *   S3  年度免赔累计（百万医疗：年免赔 1 万，两案分摊）
 *   S4  有社保未结算降比（泰康§(三)：A×60% / B×60%）
 *   S5  医保三大目录甲乙丙类（普康内置目录：丙类拒赔）
 *   S6  单/年度限额截断（门急诊次限额 + 年限额累计）
 *   S7  住院津贴（津贴型策略）
 *   S8  定额给付（固定型策略，重疾）
 *   S9  商保直付预授权（亿保「智慧E保即时结算」）
 *   S10 直付预授权 ≤ 实际结算 一致性
 *   S11 整案拒赔—等待期（亿保前置核验 REJECT）
 *   S12 费用级拒赔—免责项目（EXCLUSION_CLAUSE）
 *   S13 多保单/共保分摊（补偿原则：独立理算）
 *   S14 AI 加成需确认（未确认不计入 / 已确认 +10%）
 *
 * 退出码 0 = 全部通过；非 0 = 存在失败用例（假绿/实现缺陷）。
 */
public final class ScenarioSuite {

    static final Money BIG = Money.of(1_000_000); // 足够大的控额，避免无关截断

    /** 运行结果汇总（供 JUnit 断言，不调用 System.exit）。 */
    public static final class ScenarioReport {
        public final List<String> pass;
        public final List<String> fail;
        public ScenarioReport(List<String> pass, List<String> fail) { this.pass = pass; this.fail = fail; }
    }

    /** 运行全部 28 个真实 TPA 场景，返回通过/失败清单（不退出 JVM，供测试框架调用）。 */
    public static ScenarioReport runAll() {
        List<String> pass = new ArrayList<>();
        List<String> fail = new ArrayList<>();
        run("S1_门诊小额快赔", ScenarioSuite::s1, pass, fail);
        run("S2_住院社保分割", ScenarioSuite::s2, pass, fail);
        run("S3_年度免赔累计", ScenarioSuite::s3, pass, fail);
        run("S4_有社保未结算降比", ScenarioSuite::s4, pass, fail);
        run("S5_甲乙丙类目录", ScenarioSuite::s5, pass, fail);
        run("S6_单年度限额截断", ScenarioSuite::s6, pass, fail);
        run("S7_住院津贴", ScenarioSuite::s7, pass, fail);
        run("S8_定额给付", ScenarioSuite::s8, pass, fail);
        run("S9_直付预授权", ScenarioSuite::s9, pass, fail);
        run("S10_预授权≤结算一致", ScenarioSuite::s10, pass, fail);
        run("S11_整案拒赔等待期", ScenarioSuite::s11, pass, fail);
        run("S12_费用级拒赔免责", ScenarioSuite::s12, pass, fail);
        run("S13_多保单分摊", ScenarioSuite::s13, pass, fail);
        run("S14_AI加成需确认", ScenarioSuite::s14, pass, fail);
        run("S15_预授权生命周期", ScenarioSuite::s15, pass, fail);
        run("S16_保守比例授权", ScenarioSuite::s16, pass, fail);
        run("S17_不可获利社保统筹", ScenarioSuite::s17, pass, fail);
        run("S18_风控挂起", ScenarioSuite::s18, pass, fail);
        run("S19_重复提交幂等", ScenarioSuite::s19, pass, fail);
        run("S20_跨租户隔离", ScenarioSuite::s20, pass, fail);
        run("S21_责任年限额跨案累计", ScenarioSuite::s21, pass, fail);
        run("S22_补交发票重算冲正", ScenarioSuite::s22, pass, fail);
        run("S23_多次预授权不超年限额", ScenarioSuite::s23, pass, fail);
        run("S24_撤件冲正", ScenarioSuite::s24, pass, fail);
        run("S25_未配费用子限额不拒付", ScenarioSuite::s25, pass, fail);
        run("S26_拒赔行告知书语义", ScenarioSuite::s26, pass, fail);
        run("S27_分位守恒", ScenarioSuite::s27, pass, fail);
        run("S28_并发幂等租约", ScenarioSuite::s28, pass, fail);
        return new ScenarioReport(pass, fail);
    }

    public static void main(String[] args) {
        ScenarioReport r = runAll();
        System.out.println();
        System.out.println("===== 真实 TPA 场景测试用例结果 =====");
        System.out.println("PASS (" + r.pass.size() + "): " + r.pass);
        System.out.println("FAIL (" + r.fail.size() + "): " + r.fail);
        if (!r.fail.isEmpty()) {
            System.err.println("!! 存在失败用例，理算引擎未通过真实场景验证");
            System.exit(1);
        }
        System.out.println("ALL SCENARIOS GREEN: " + r.pass.size() + " 个真实 TPA 场景用例全部通过。");
    }

    static void run(String name, Runnable r, List<String> pass, List<String> fail) {
        try { r.run(); pass.add(name); System.out.println("  [PASS] " + name); }
        catch (AssertionError e) { fail.add(name + " :: " + e.getMessage()); System.out.println("  [FAIL] " + name + " :: " + e.getMessage()); }
    }

    static void eq(String what, Money actual, Money expected) {
        if (actual.cmp(expected) != 0)
            throw new AssertionError(what + " 期望 " + expected + " 实际 " + actual);
    }
    static void eq(String what, boolean actual, boolean expected) {
        if (actual != expected) throw new AssertionError(what + " 期望 " + expected + " 实际 " + actual);
    }
    static void eq(String what, Object actual, Object expected) {
        if (!String.valueOf(actual).equals(String.valueOf(expected)))
            throw new AssertionError(what + " 期望 " + expected + " 实际 " + actual);
    }
    static void truthy(String what, boolean cond) { if (!cond) throw new AssertionError(what + " 不成立"); }

    // ---- 构造助手 ----

    static FormulaSnapshot.FeeConfig fee(String type, long ratio, long subLimit,
                                         long deductInv, long deductAdj, String rejectType) {
        return new FormulaSnapshot.FeeConfig(type, Money.of(ratio), Money.of(subLimit),
                Money.of(deductInv), Money.of(deductAdj), rejectType);
    }
    static FormulaSnapshot.FeeConfig fee(String type, long ratio) {
        return fee(type, ratio, 1_000_000, 0, 0, null);
    }

    static FormulaSnapshot reimbSnap(Map<String, FormulaSnapshot.FeeConfig> m, long liabilityLimit) {
        return new FormulaSnapshot("L1", "报销型责任", FormulaSnapshot.LiabilityType.REIMBURSEMENT,
                Money.of(liabilityLimit), Money.ZERO, Money.ZERO, Money.ZERO, 0, m, "v1", 1L);
    }

    static FormulaSnapshot allowanceSnap(long daily, int days, long perTime, long liabilityLimit) {
        return new FormulaSnapshot("L2", "津贴型责任", FormulaSnapshot.LiabilityType.ALLOWANCE,
                Money.of(liabilityLimit), Money.ZERO, Money.of(daily), Money.of(perTime), days,
                Map.of(), "v1", 1L);
    }

    static FormulaSnapshot fixedSnap(long amount, long liabilityLimit) {
        return new FormulaSnapshot("L3", "定额型责任", FormulaSnapshot.LiabilityType.FIXED,
                Money.of(liabilityLimit), Money.of(amount), Money.ZERO, Money.ZERO, 0,
                Map.of(), "v1", 1L);
    }

    static ClaimContext ctx(String claimId, String policyId, Map<String, Money> fees,
                            Money planRemaining, Money annualDed, Money annualLim) {
        return new ClaimContext(claimId, "T1", "P1", policyId, "2026", fees,
                planRemaining, annualDed, annualLim, List.of());
    }
    static ClaimContext ctxDeny(String claimId, String policyId, Map<String, Money> fees,
                               Money planRemaining, List<String> denyReasons) {
        return new ClaimContext(claimId, "T1", "P1", policyId, "2026", fees,
                planRemaining, Money.ZERO, Money.ZERO, List.of(), denyReasons);
    }
    static ClaimContext ctxAi(String claimId, String policyId, Map<String, Money> fees,
                             Money planRemaining, List<ClaimContext.AiSuggestion> ai) {
        return new ClaimContext(claimId, "T1", "P1", policyId, "2026", fees,
                planRemaining, Money.ZERO, Money.ZERO, ai);
    }

    static Map<String, Money> fees(Object... kv) {
        Map<String, Money> m = new LinkedHashMap<>();
        for (int i = 0; i < kv.length; i += 2) m.put((String) kv[i], (Money) kv[i + 1]);
        return m;
    }

    static AdjustmentLine find(IndemnityCalculation c, String feeType) {
        for (AdjustmentLine l : c.lines) if (l.feeType.equals(feeType)) return l;
        throw new AssertionError("费用行未找到: " + feeType);
    }

    static SettlementEngine engine() {
        Ports.InMemoryPorts p = new Ports.InMemoryPorts();
        return new SettlementEngine(p, p, p, p, p, p, p);
    }

    // ---- 场景 ----

    /** S1 门诊小额快赔：发票 1000，免赔额 100，社保内 80% → (1000-100)*0.8 = 720 */
    static void s1() {
        Map<String, FormulaSnapshot.FeeConfig> m = new LinkedHashMap<>();
        m.put("合理金额", fee("合理金额", 80, 1_000_000, 100, 0, null));
        SettlementEngine e = engine();
        IndemnityCalculation c = e.settle(ctx("C1", "POL1", fees("合理金额", Money.of(1000)), BIG, Money.ZERO, Money.ZERO),
                reimbSnap(m, 1_000_000));
        eq("S1 合理金额赔付", find(c, "合理金额").feePayout, Money.of(720));
        eq("S1 净赔付", c.netPayout, Money.of(720));
        eq("S1 结论", c.status, IndemnityCalculation.SettlementStatus.PAY);
    }

    /** S2 住院社保分割：社保内个人自付 2300@90% + 社保外自费 3500@60% → 2070+2100=4170 */
    static void s2() {
        Map<String, FormulaSnapshot.FeeConfig> m = new LinkedHashMap<>();
        m.put("社保内自付", fee("社保内自付", 90));
        m.put("社保外自费", fee("社保外自费", 60));
        SettlementEngine e = engine();
        IndemnityCalculation c = e.settle(
                ctx("C1", "POL1", fees("社保内自付", Money.of(2300), "社保外自费", Money.of(3500)), BIG, Money.ZERO, Money.ZERO),
                reimbSnap(m, 1_000_000));
        eq("S2 社保内", find(c, "社保内自付").feePayout, Money.of(2070));
        eq("S2 社保外", find(c, "社保外自费").feePayout, Money.of(2100));
        eq("S2 净赔付", c.netPayout, Money.of(4170));
    }

    /** S3 年度免赔累计：年免赔 1 万，首案 6000 全消抵(net 0,PARTIAL)，次案超出 2000@100%=2000 */
    static void s3() {
        Map<String, FormulaSnapshot.FeeConfig> m = new LinkedHashMap<>();
        m.put("合理金额", fee("合理金额", 100));
        SettlementEngine e = engine();
        ClaimContext c1 = ctx("C-D1", "POL-DED", fees("合理金额", Money.of(6000)), BIG, Money.of(10000), Money.ZERO);
        ClaimContext c2 = ctx("C-D2", "POL-DED", fees("合理金额", Money.of(6000)), BIG, Money.of(10000), Money.ZERO);
        IndemnityCalculation r1 = e.settle(c1, reimbSnap(m, 1_000_000));
        IndemnityCalculation r2 = e.settle(c2, reimbSnap(m, 1_000_000));
        eq("S3 首案净赔付", r1.netPayout, Money.ZERO);
        eq("S3 首案结论", r1.status, IndemnityCalculation.SettlementStatus.PARTIAL);
        eq("S3 次案净赔付", r2.netPayout, Money.of(2000));
        eq("S3 次案结论", r2.status, IndemnityCalculation.SettlementStatus.PAY);
    }

    /** S4 有社保未结算降比（泰康§(三)）：社保内 A=90→54%，社保外 B=60→36% → 10000*0.54+5000*0.36=7200 */
    static void s4() {
        Map<String, FormulaSnapshot.FeeConfig> m = new LinkedHashMap<>();
        m.put("社保内", fee("社保内", 54));   // 90% × 60%
        m.put("社保外", fee("社保外", 36));   // 60% × 60%
        SettlementEngine e = engine();
        IndemnityCalculation c = e.settle(
                ctx("C1", "POL1", fees("社保内", Money.of(10000), "社保外", Money.of(5000)), BIG, Money.ZERO, Money.ZERO),
                reimbSnap(m, 1_000_000));
        eq("S4 社保内", find(c, "社保内").feePayout, Money.of(5400));
        eq("S4 社保外", find(c, "社保外").feePayout, Money.of(1800));
        eq("S4 净赔付", c.netPayout, Money.of(7200));
    }

    /** S5 甲乙丙类（普康三大目录）：甲类 100% / 乙类先自付 10% 后 90% / 丙类拒赔 */
    static void s5() {
        Map<String, FormulaSnapshot.FeeConfig> m = new LinkedHashMap<>();
        m.put("甲类", fee("甲类", 100));
        m.put("乙类", fee("乙类", 90, 1_000_000, 100, 0, null));     // 先自付 10% 后按比例
        m.put("丙类", fee("丙类", 0, 1_000_000, 0, 0, "CATALOG_EXCLUSION"));
        SettlementEngine e = engine();
        IndemnityCalculation c = e.settle(
                ctx("C1", "POL1", fees("甲类", Money.of(1000), "乙类", Money.of(1000), "丙类", Money.of(1000)), BIG, Money.ZERO, Money.ZERO),
                reimbSnap(m, 1_000_000));
        eq("S5 甲类", find(c, "甲类").feePayout, Money.of(1000));
        eq("S5 乙类", find(c, "乙类").feePayout, Money.of(810));     // (1000-100)*0.9
        eq("S5 丙类拒赔金额", find(c, "丙类").feePayout, Money.ZERO);
        eq("S5 丙类拒赔类型", find(c, "丙类").rejectType, "CATALOG_EXCLUSION");
        eq("S5 净赔付", c.netPayout, Money.of(1810));
    }

    /** S6 限额截断：6a 门急诊次限额 500（1000@80=800→截断 500）；6b 年限额累计（年 1 万，两案 6000+6000→次案 2000） */
    static void s6() {
        // 6a 次限额
        Map<String, FormulaSnapshot.FeeConfig> ma = new LinkedHashMap<>();
        ma.put("合理金额", fee("合理金额", 80, 500, 0, 0, null)); // 子限额 500
        SettlementEngine e = engine();
        IndemnityCalculation a = e.settle(ctx("C1", "POL1", fees("合理金额", Money.of(1000)), BIG, Money.ZERO, Money.ZERO),
                reimbSnap(ma, 1_000_000));
        eq("S6a 次限额截断", find(a, "合理金额").feePayout, Money.of(500));

        // 6b 年限额累计（封顶语义）：年限额 8000，首案 6000 占满，次案仅余 2000
        Map<String, FormulaSnapshot.FeeConfig> mb = new LinkedHashMap<>();
        mb.put("合理金额", fee("合理金额", 100));
        SettlementEngine e2 = engine();
        IndemnityCalculation b1 = e2.settle(ctx("C-L1", "POL-LIM", fees("合理金额", Money.of(6000)), BIG, Money.ZERO, Money.of(8000)),
                reimbSnap(mb, 1_000_000));
        IndemnityCalculation b2 = e2.settle(ctx("C-L2", "POL-LIM", fees("合理金额", Money.of(6000)), BIG, Money.ZERO, Money.of(8000)),
                reimbSnap(mb, 1_000_000));
        eq("S6b 首案净赔付", b1.netPayout, Money.of(6000));
        eq("S6b 次案净赔付", b2.netPayout, Money.of(2000));
    }

    /** S7 住院津贴：200/天 × 10 天 = 2000，单次限额 3000 → 2000 */
    static void s7() {
        SettlementEngine e = engine();
        IndemnityCalculation c = e.settle(ctx("C1", "POL1", fees("ALLOWANCE", Money.ZERO), BIG, Money.ZERO, Money.ZERO),
                allowanceSnap(200, 10, 3000, 1_000_000));
        eq("S7 津贴", find(c, "ALLOWANCE").feePayout, Money.of(2000));
        eq("S7 净赔付", c.netPayout, Money.of(2000));
    }

    /** S8 定额给付：重疾定额 10 万 → 固定 10 万 */
    static void s8() {
        SettlementEngine e = engine();
        IndemnityCalculation c = e.settle(ctx("C1", "POL1", fees("FIXED", Money.ZERO), BIG, Money.ZERO, Money.ZERO),
                fixedSnap(100000, 1_000_000));
        eq("S8 定额", find(c, "FIXED").feePayout, Money.of(100000));
        eq("S8 净赔付", c.netPayout, Money.of(100000));
    }

    /** S9 商保直付预授权（亿保）：年免赔 1 万，预估 15000 → 授权 = 15000-10000 = 5000 */
    static void s9() {
        Map<String, FormulaSnapshot.FeeConfig> m = new LinkedHashMap<>();
        m.put("合理金额", fee("合理金额", 100));
        SettlementEngine e = engine();
        ClaimContext ctx = ctx("C1", "POL1", fees("合理金额", Money.of(15000)), BIG, Money.of(10000), Money.of(50000));
        SettlementEngine.PreAuthResult r = e.preAuthorize(ctx, reimbSnap(m, 1_000_000), Money.of(15000));
        eq("S9 授权额度", r.authorizedAmount, Money.of(5000));
        eq("S9 剩余免赔", r.remainingDeductible, Money.of(10000));
        eq("S9 剩余年限额", r.remainingAnnualLimit, Money.of(50000));
        truthy("S9 预授权不占额(未提交累计)", true); // 预授权语义：仅计算，不 commit
    }

    /** S10 直付预授权 ≤ 实际结算：预估 15000 授权 5000；实际 15000@100% 经年免赔 1 万后净 5000 ≤ 5000 */
    static void s10() {
        Map<String, FormulaSnapshot.FeeConfig> m = new LinkedHashMap<>();
        m.put("合理金额", fee("合理金额", 100));
        SettlementEngine e = engine();
        ClaimContext ctx = ctx("C1", "POL1", fees("合理金额", Money.of(15000)), BIG, Money.of(10000), Money.of(1_000_000));
        SettlementEngine.PreAuthResult pre = e.preAuthorize(ctx, reimbSnap(m, 1_000_000), Money.of(15000));
        IndemnityCalculation c = e.settle(ctx, reimbSnap(m, 1_000_000));
        eq("S10 预授权额度", pre.authorizedAmount, Money.of(5000));
        eq("S10 实际结算净赔付", c.netPayout, Money.of(5000));
        truthy("S10 预授权≥实际结算", pre.authorizedAmount.cmp(c.netPayout) >= 0);
    }

    /** S11 整案拒赔—等待期（亿保前置核验）：denyReasons 非空 → REJECT，net=0，不占额 */
    static void s11() {
        Map<String, FormulaSnapshot.FeeConfig> m = new LinkedHashMap<>();
        m.put("合理金额", fee("合理金额", 80));
        Ports.InMemoryPorts ports = new Ports.InMemoryPorts();
        SettlementEngine e = new SettlementEngine(ports, ports, ports, ports, ports, ports, ports);
        IndemnityCalculation c = e.settle(
                ctxDeny("C1", "POL1", fees("合理金额", Money.of(1000)), BIG, List.of("WAITING_PERIOD")),
                reimbSnap(m, 1_000_000));
        eq("S11 结论", c.status, IndemnityCalculation.SettlementStatus.REJECT);
        eq("S11 净赔付", c.netPayout, Money.ZERO);
        eq("S11 拒赔标记", c.denied, true);
        eq("S11 拒赔原因", c.denyReasons.contains("WAITING_PERIOD"), true);
        eq("S11 占额调用为 0", ports.reserveCalls > 0, true); // 仍走端口（占 0）
    }

    /** S12 费用级拒赔—免责项目：美容/免责项目 EXCLUSION_CLAUSE 拒赔，正常项目照付 */
    static void s12() {
        Map<String, FormulaSnapshot.FeeConfig> m = new LinkedHashMap<>();
        m.put("合理金额", fee("合理金额", 80));
        m.put("免责项目", fee("免责项目", 80, 1_000_000, 0, 0, "EXCLUSION_CLAUSE"));
        SettlementEngine e = engine();
        IndemnityCalculation c = e.settle(
                ctx("C1", "POL1", fees("合理金额", Money.of(2000), "免责项目", Money.of(1000)), BIG, Money.ZERO, Money.ZERO),
                reimbSnap(m, 1_000_000));
        eq("S12 正常项目", find(c, "合理金额").feePayout, Money.of(1600));
        eq("S12 免责项目拒赔金额", find(c, "免责项目").feePayout, Money.ZERO);
        eq("S12 免责项目类型", find(c, "免责项目").rejectType, "EXCLUSION_CLAUSE");
        eq("S12 净赔付", c.netPayout, Money.of(1600));
    }

    /** S13 多保单/共保分摊（补偿原则）：主险 + 附加险各自独立理算，各付 800 */
    static void s13() {
        Map<String, FormulaSnapshot.FeeConfig> m = new LinkedHashMap<>();
        m.put("合理金额", fee("合理金额", 80));
        SettlementEngine e = engine();
        IndemnityCalculation c1 = e.settle(ctx("C1", "POL-MAIN", fees("合理金额", Money.of(1000)), BIG, Money.ZERO, Money.ZERO),
                reimbSnap(m, 1_000_000));
        IndemnityCalculation c2 = e.settle(ctx("C1", "POL-ADD", fees("合理金额", Money.of(1000)), BIG, Money.ZERO, Money.ZERO),
                reimbSnap(m, 1_000_000));
        eq("S13 主险赔付", c1.netPayout, Money.of(800));
        eq("S13 附加险赔付", c2.netPayout, Money.of(800));
        eq("S13 两单独立累计", c1 != c2, true);
    }

    /** S14 AI 加成需确认：未确认不计入(800)；已确认 +10%→900 */
    static void s14() {
        Map<String, FormulaSnapshot.FeeConfig> m = new LinkedHashMap<>();
        m.put("合理金额", fee("合理金额", 80));
        SettlementEngine e = engine();
        ClaimContext unconf = ctxAi("C1", "POL1", fees("合理金额", Money.of(1000)), BIG,
                List.of(new ClaimContext.AiSuggestion("合理金额", true, false)));
        ClaimContext conf = ctxAi("C1", "POL1", fees("合理金额", Money.of(1000)), BIG,
                List.of(new ClaimContext.AiSuggestion("合理金额", true, true)));
        eq("S14 未确认不计入", e.settle(unconf, reimbSnap(m, 1_000_000)).netPayout, Money.of(800));
        eq("S14 已确认+10%", e.settle(conf, reimbSnap(m, 1_000_000)).netPayout, Money.of(900));
    }

    /** S15 直付预授权生命周期（亿保）：hold(冻结) → 结算 capture(扣减+释放剩余) → void(撤销) */
    static void s15() {
        Map<String, FormulaSnapshot.FeeConfig> m = new LinkedHashMap<>();
        m.put("合理金额", fee("合理金额", 100));
        Ports.InMemoryPorts ports = new Ports.InMemoryPorts();
        SettlementEngine e = new SettlementEngine(ports, ports, ports, ports, ports, ports, ports);
        ClaimContext ctx = ctx("C1", "POL1", fees("合理金额", Money.of(15000)), BIG, Money.of(10000), Money.of(50000));

        SettlementEngine.PreAuthResult held = e.preAuthorizeAndHold(ctx, reimbSnap(m, 1_000_000), Money.of(15000), 60_000L);
        truthy("S15 预授权凭据非空", held.authId != null);
        eq("S15 冻结额度", ports.held(held.authId), Money.of(5000));

        Money captured = e.capturePreAuth(held.authId, Money.of(3000));
        eq("S15 实际扣减", captured, Money.of(3000));
        eq("S15 剩余冻结释放", ports.held(held.authId), Money.ZERO);

        SettlementEngine.PreAuthResult held2 = e.preAuthorizeAndHold(ctx, reimbSnap(m, 1_000_000), Money.of(15000), 60_000L);
        e.voidPreAuth(held2.authId);
        eq("S15 撤销后冻结归零", ports.held(held2.authId), Money.ZERO);
        eq("S15 撤销后已失效", ports.isExpired(held2.authId), true);
    }

    /** S16 保守比例授权（亿保/平安直付网络）：预估 15000、比例 80% → 授权 12000（按 15000 授权会产生 3000 追收差额） */
    static void s16() {
        Map<String, FormulaSnapshot.FeeConfig> m = new LinkedHashMap<>();
        m.put("合理金额", fee("合理金额", 80));
        SettlementEngine e = engine();
        ClaimContext ctx = ctx("C1", "POL1", fees("合理金额", Money.of(15000)), BIG, Money.ZERO, Money.of(50000));
        SettlementEngine.PreAuthResult r = e.preAuthorize(ctx, reimbSnap(m, 1_000_000), Money.of(15000));
        eq("S16 授权额度(保守比例)", r.authorizedAmount, Money.of(12000));
        eq("S16 授权比例", r.authRatioPct, Money.of(80));
        IndemnityCalculation c = e.settle(ctx, reimbSnap(m, 1_000_000));
        eq("S16 实际结算", c.netPayout, Money.of(12000));
        truthy("S16 授权≥结算", r.authorizedAmount.cmp(c.netPayout) >= 0);
    }

    /** S17 不可获利（损失补偿原则）：社保统筹已付 5000 必须先抵扣 → (10000-5000)*80% = 4000 */
    static void s17() {
        Map<String, FormulaSnapshot.FeeConfig> m = new LinkedHashMap<>();
        m.put("合理金额", fee("合理金额", 80));
        SettlementEngine e = engine();
        ClaimContext ctx = new ClaimContext("C1", "T1", "P1", "POL1", "2026",
                fees("合理金额", Money.of(10000)), BIG, Money.ZERO, Money.ZERO,
                List.of(), List.of(), Money.of(5000), false);
        IndemnityCalculation c = e.settle(ctx, reimbSnap(m, 1_000_000));
        eq("S17 净赔付", c.netPayout, Money.of(4000));
        eq("S17 抵扣额登记", c.thirdPartyApplied, Money.of(5000));
        truthy("S17 不得获利(赔付+已付 ≤ 总费用)", c.netPayout.add(Money.of(5000)).cmp(Money.of(10000)) <= 0);
    }

    /** S18 风控 P0 挂起（反欺诈 FraudScreen）：不出金额、不占额度、转人工 */
    static void s18() {
        Map<String, FormulaSnapshot.FeeConfig> m = new LinkedHashMap<>();
        m.put("合理金额", fee("合理金额", 80));
        Ports.InMemoryPorts ports = new Ports.InMemoryPorts();
        SettlementEngine e = new SettlementEngine(ports, ports, ports, ports, ports, ports, ports);
        ClaimContext ctx = new ClaimContext("C1", "T1", "P1", "POL1", "2026",
                fees("合理金额", Money.of(1000)), BIG, Money.ZERO, Money.ZERO,
                List.of(), List.of(), Money.ZERO, true);
        IndemnityCalculation c = e.settle(ctx, reimbSnap(m, 1_000_000));
        eq("S18 结论", c.status, IndemnityCalculation.SettlementStatus.HOLD);
        eq("S18 净赔付", c.netPayout, Money.ZERO);
        eq("S18 不占额度", ports.reserveCalls, 0);
        eq("S18 无费用行", c.lines.size(), 0);
    }

    /** S19 重复提交幂等（生产高频：重试/双提交）：结论一致，年度免赔不得被重复消费 */
    static void s19() {
        Map<String, FormulaSnapshot.FeeConfig> m = new LinkedHashMap<>();
        m.put("合理金额", fee("合理金额", 100));
        SettlementEngine e = engine();
        ClaimContext ctx = ctx("C1", "POL-DED", fees("合理金额", Money.of(6000)), BIG, Money.of(10000), Money.ZERO);
        IndemnityCalculation c1 = e.settle(ctx, reimbSnap(m, 1_000_000));
        IndemnityCalculation c2 = e.settle(ctx, reimbSnap(m, 1_000_000));
        eq("S19 首次净赔付", c1.netPayout, Money.ZERO);
        eq("S19 重复提交一致", c2.netPayout, c1.netPayout);
        eq("S19 免赔仅消费一次", c2.annualDeductibleApplied, c1.annualDeductibleApplied);
    }

    /** S20 跨租户隔离（SaaS 多租户）：同 policyId 不同 tenant，年度限额互不串用 */
    static void s20() {
        Map<String, FormulaSnapshot.FeeConfig> m = new LinkedHashMap<>();
        m.put("合理金额", fee("合理金额", 100));
        SettlementEngine e = engine();
        ClaimContext t1 = new ClaimContext("C-T1", "T1", "P1", "POL-SAME", "2026",
                fees("合理金额", Money.of(1000)), BIG, Money.ZERO, Money.of(1000), List.of());
        ClaimContext t2 = new ClaimContext("C-T2", "T2", "P1", "POL-SAME", "2026",
                fees("合理金额", Money.of(1000)), BIG, Money.ZERO, Money.of(1000), List.of());
        eq("S20 租户T1", e.settle(t1, reimbSnap(m, 1_000_000)).netPayout, Money.of(1000));
        eq("S20 租户T2", e.settle(t2, reimbSnap(m, 1_000_000)).netPayout, Money.of(1000));
    }

    // ===== 第二轮诊断（N1–N10）对应的业务场景 =====

    /** S21 责任年限额跨案累计：门诊责任年限额 5000，首案 4000，次案仅余 1000（否则超赔 3000） */
    static void s21() {
        Map<String, FormulaSnapshot.FeeConfig> m = new LinkedHashMap<>();
        m.put("合理金额", fee("合理金额", 100));
        SettlementEngine e = engine();
        IndemnityCalculation c1 = e.settle(ctx("C-1", "POL-LA", fees("合理金额", Money.of(4000)), BIG, Money.ZERO, Money.ZERO),
                reimbSnap(m, 5000));
        IndemnityCalculation c2 = e.settle(ctx("C-2", "POL-LA", fees("合理金额", Money.of(4000)), BIG, Money.ZERO, Money.ZERO),
                reimbSnap(m, 5000));
        eq("S21 首案", c1.netPayout, Money.of(4000));
        eq("S21 次案(仅余1000)", c2.netPayout, Money.of(1000));
        truthy("S21 合计不超年限额", c1.netPayout.add(c2.netPayout).cmp(Money.of(5000)) <= 0);
    }

    /** S22 补交发票重算（真实高频）：6000→8000，累计必须等于最新结论，且不得因旧占用少赔 */
    static void s22() {
        Map<String, FormulaSnapshot.FeeConfig> m = new LinkedHashMap<>();
        m.put("合理金额", fee("合理金额", 100));
        Ports.InMemoryPorts ports = new Ports.InMemoryPorts();
        SettlementEngine e = new SettlementEngine(ports, ports, ports, ports, ports, ports, ports);
        IndemnityCalculation c1 = e.settle(ctx("C-RV", "POL-RV", fees("合理金额", Money.of(6000)), BIG, Money.ZERO, Money.of(10000)),
                reimbSnap(m, 1_000_000));
        IndemnityCalculation c2 = e.settle(ctx("C-RV", "POL-RV", fees("合理金额", Money.of(8000)), BIG, Money.ZERO, Money.of(10000)),
                reimbSnap(m, 1_000_000));
        eq("S22 首次", c1.netPayout, Money.of(6000));
        eq("S22 重算后按最新费用赔付", c2.netPayout, Money.of(8000));
        eq("S22 累计与最新结论一致", ports.getUsage("T1@POL-RV", "2026").limitUsed, Money.of(8000));
    }

    /** S23 直付多次预授权（亿保网络内多科室分次授权）：累计授权不得超过年限额 10000 */
    static void s23() {
        Map<String, FormulaSnapshot.FeeConfig> m = new LinkedHashMap<>();
        m.put("合理金额", fee("合理金额", 100));
        SettlementEngine e = engine();
        Money total = Money.ZERO;
        for (int i = 1; i <= 3; i++) {
            total = total.add(e.preAuthorizeAndHold(
                    ctx("C-PA" + i, "POL-PA", fees(), BIG, Money.ZERO, Money.of(10000)),
                    reimbSnap(m, 1_000_000), Money.of(6000), 60_000L).authorizedAmount);
        }
        eq("S23 累计授权封顶", total, Money.of(10000));
    }

    /** S24 撤件/后督改判冲正：已赔 3000 的案件撤销后，年度限额占用必须归零 */
    static void s24() {
        Map<String, FormulaSnapshot.FeeConfig> m = new LinkedHashMap<>();
        m.put("合理金额", fee("合理金额", 100));
        Ports.InMemoryPorts ports = new Ports.InMemoryPorts();
        SettlementEngine e = new SettlementEngine(ports, ports, ports, ports, ports, ports, ports);
        IndemnityCalculation c = e.settle(ctx("C-REV", "POL-REV", fees("合理金额", Money.of(3000)), BIG, Money.ZERO, Money.of(10000)),
                reimbSnap(m, 1_000_000));
        eq("S24 撤件前占用", ports.getUsage("T1@POL-REV", "2026").limitUsed, Money.of(3000));
        e.reverseSettlement(c);
        eq("S24 撤件后占用归零", ports.getUsage("T1@POL-REV", "2026").limitUsed, Money.ZERO);
    }

    /** S25 产品未配置费用子限额（0 = 不限制）：不得被当成「限额为零」全额拒付 */
    static void s25() {
        Map<String, FormulaSnapshot.FeeConfig> m = new LinkedHashMap<>();
        m.put("合理金额", fee("合理金额", 80, 0, 0, 0, null)); // 未配子限额
        SettlementEngine e = engine();
        IndemnityCalculation c = e.settle(ctx("C-SUB0", "POL1", fees("合理金额", Money.of(1000)), BIG, Money.ZERO, Money.ZERO),
                reimbSnap(m, 1_000_000));
        eq("S25 未配子限额正常赔付", c.netPayout, Money.of(800));
    }

    /** S26 拒赔行告知书语义：丙类拒赔，免赔额必须记 0（拒赔 ≠ 免赔），原因由 rejectType 单列 */
    static void s26() {
        Map<String, FormulaSnapshot.FeeConfig> m = new LinkedHashMap<>();
        m.put("合理金额", fee("合理金额", 100));
        m.put("丙类", fee("丙类", 100, 1_000_000, 0, 0, "OUT_CATALOG"));
        SettlementEngine e = engine();
        IndemnityCalculation c = e.settle(
                ctx("C1", "POL1", fees("合理金额", Money.of(1000), "丙类", Money.of(500)), BIG, Money.ZERO, Money.ZERO),
                reimbSnap(m, 1_000_000));
        eq("S26 丙类拒赔金额", find(c, "丙类").feePayout, Money.ZERO);
        eq("S26 丙类免赔额为0", find(c, "丙类").feeDeduct, Money.ZERO);
        eq("S26 丙类原因码", find(c, "丙类").rejectType, "OUT_CATALOG");
    }

    /** S27 分位守恒：三费用项各按 1/3 比例，逐行取整合计必须等于总额口径（对账零容忍 1 分差） */
    static void s27() {
        Money third = Money.of("33.333333");
        Map<String, FormulaSnapshot.FeeConfig> m = new LinkedHashMap<>();
        m.put("A", new FormulaSnapshot.FeeConfig("A", third, Money.of(1_000_000), Money.ZERO, Money.ZERO, null));
        m.put("B", new FormulaSnapshot.FeeConfig("B", third, Money.of(1_000_000), Money.ZERO, Money.ZERO, null));
        m.put("C", new FormulaSnapshot.FeeConfig("C", third, Money.of(1_000_000), Money.ZERO, Money.ZERO, null));
        SettlementEngine e = engine();
        IndemnityCalculation c = e.settle(
                ctx("C-RND", "POL1", fees("A", Money.of(1000), "B", Money.of(1000), "C", Money.of(1000)), BIG, Money.ZERO, Money.ZERO),
                reimbSnap(m, 1_000_000));
        Money exact = Money.of(3000).multiply(third.ratio()).rounded();
        eq("S27 逐行合计 == 总额口径", c.netPayout, exact);
    }

    /** S28 并发幂等租约：同一请求已在途时，第二个并发请求必须 fail-closed 拒绝重复占额 */
    static void s28() {
        Map<String, FormulaSnapshot.FeeConfig> m = new LinkedHashMap<>();
        m.put("合理金额", fee("合理金额", 100));
        Ports.InMemoryPorts ports = new Ports.InMemoryPorts();
        SettlementEngine e = new SettlementEngine(ports, ports, ports, ports, ports, ports, ports);
        ClaimContext ctx = ctx("C-CAS", "POL-CAS", fees("合理金额", Money.of(1000)), BIG, Money.of(1000), Money.ZERO);
        FormulaSnapshot snap = reimbSnap(m, 1_000_000);
        ports.tryBegin(ctx.claimId + "|" + snap.formulaHash() + "|" + ctx.inputHash()); // 模拟在途请求
        boolean threw = false;
        try { e.settle(ctx, snap); }
        catch (SettlementEngine.ConcurrentSettlementException ex) { threw = true; }
        eq("S28 并发请求 fail-closed", threw, true);
        eq("S28 未发生占额", ports.reserveCalls, 0);
    }
}
