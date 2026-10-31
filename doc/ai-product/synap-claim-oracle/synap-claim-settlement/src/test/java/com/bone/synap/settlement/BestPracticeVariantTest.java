package com.bone.synap.settlement;

import com.bone.synap.settlement.domain.ClaimContext;
import com.bone.synap.settlement.domain.FormulaSnapshot;
import com.bone.synap.settlement.domain.IndemnityCalculation;
import com.bone.synap.settlement.engine.EngineFlags;
import com.bone.synap.settlement.engine.SettlementEngine;
import com.bone.synap.settlement.port.Ports;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * README 已知限制 #1–#3 的行业最佳实践变体（防假绿）。
 * 三条变体均**默认关闭 = 沿用现行语义（零回归）**，开启 = 行业最佳实践口径；
 * 每条用例同时断言「默认语义」与「变体语义」两种结果，确保变体真能改变结果、且默认路径不受影响。
 * 引擎用内存端口（Ports.InMemoryPorts）驱动，无需数据库。
 */
public class BestPracticeVariantTest {

    private static final Money BIG = Money.of(1_000_000);

    private static SettlementEngine engine(EngineFlags flags) {
        Ports.InMemoryPorts p = new Ports.InMemoryPorts();
        return new SettlementEngine(p, p, p, p, p, p, p, flags);
    }

    private static EngineFlags flags() { return EngineFlags.allOn(); }

    private static FormulaSnapshot reimb(Map<String, FormulaSnapshot.FeeConfig> m) {
        return new FormulaSnapshot("L1", "报销型责任", FormulaSnapshot.LiabilityType.REIMBURSEMENT,
                Money.ZERO, Money.ZERO, Money.ZERO, Money.ZERO, 0, m, "v1", 1L);
    }

    // ---- #1 第三方已付按费用类别归因（社保内优先） ----
    @Test
    void thirdPartyPaidByCategory_prioritizesSocial() {
        Map<String, FormulaSnapshot.FeeConfig> m = new LinkedHashMap<>();
        m.put("社保内", new FormulaSnapshot.FeeConfig("社保内", Money.of(100), BIG, Money.ZERO, Money.ZERO, null, Boolean.TRUE));
        m.put("社保外", new FormulaSnapshot.FeeConfig("社保外", Money.of(100), BIG, Money.ZERO, Money.ZERO, null, Boolean.FALSE));
        FormulaSnapshot snap = reimb(m);

        Map<String, Money> f = new LinkedHashMap<>();
        f.put("社保内", Money.of(1000));
        f.put("社保外", Money.of(1000));

        // 变体开启：600 第三方已付优先核销社保内 -> 社保内 feePayout=400，社保外=1000
        EngineFlags on = flags(); on.thirdPartyPaidByCategory = true;
        ClaimContext ctxOn = new ClaimContext("C1", "T1", "P1", "POL", "2026", f, BIG, Money.ZERO, Money.ZERO,
                List.of(), List.of(), Money.of(600), false);
        IndemnityCalculation onC = engine(on).settle(ctxOn, snap);
        assertEquals(0, Money.of(400).cmp(linePayout(onC, "社保内")), "社保内应被第三方已付核销至 400");
        assertEquals(0, Money.of(1000).cmp(linePayout(onC, "社保外")), "社保外不应被核销");
        assertEquals(0, Money.of(600).cmp(onC.thirdPartyApplied), "第三方已付抵扣合计应为 600");

        // 默认关闭：600 按比例分摊到两行（各 300）-> 各 feePayout=700
        EngineFlags off = flags();
        ClaimContext ctxOff = new ClaimContext("C2", "T1", "P1", "POL", "2026", f, BIG, Money.ZERO, Money.ZERO,
                List.of(), List.of(), Money.of(600), false);
        IndemnityCalculation offC = engine(off).settle(ctxOff, snap);
        assertEquals(0, Money.of(700).cmp(linePayout(offC, "社保内")), "默认：社保内按比例分摊至 700");
        assertEquals(0, Money.of(700).cmp(linePayout(offC, "社保外")), "默认：社保外按比例分摊至 700");
    }

    // ---- #2 年度限额按比例摊薄（而非先到先得） ----
    @Test
    void annualLimitProportional_apportionsPoolNotFirstCome() {
        Map<String, FormulaSnapshot.FeeConfig> m = new LinkedHashMap<>();
        m.put("A", new FormulaSnapshot.FeeConfig("A", Money.of(100), BIG, Money.ZERO, Money.ZERO, null));
        m.put("B", new FormulaSnapshot.FeeConfig("B", Money.of(100), BIG, Money.ZERO, Money.ZERO, null));
        FormulaSnapshot snap = reimb(m);

        Map<String, Money> f = new LinkedHashMap<>();
        f.put("A", Money.of(1000));
        f.put("B", Money.of(1000));

        // 默认关闭：行序靠前者(A)独吞限额 -> A=1000, B=0
        EngineFlags off = flags();
        ClaimContext ctxOff = new ClaimContext("C1", "T1", "P1", "POL", "2026", f, BIG, Money.ZERO, Money.of(1000), List.of());
        IndemnityCalculation offC = engine(off).settle(ctxOff, snap);
        assertEquals(0, Money.of(1000).cmp(linePayout(offC, "A")), "默认：A 独吞限额");
        assertEquals(0, Money.of(0).cmp(linePayout(offC, "B")), "默认：B 被顺序截断为 0");

        // 变体开启：限额按比例摊薄 -> A=500, B=500
        EngineFlags on = flags(); on.annualLimitProportional = true;
        ClaimContext ctxOn = new ClaimContext("C2", "T1", "P1", "POL", "2026", f, BIG, Money.ZERO, Money.of(1000), List.of());
        IndemnityCalculation onC = engine(on).settle(ctxOn, snap);
        assertEquals(0, Money.of(500).cmp(linePayout(onC, "A")), "变体：A 按比例得 500");
        assertEquals(0, Money.of(500).cmp(linePayout(onC, "B")), "变体：B 按比例得 500");
    }

    // ---- #3 多保单 ISO 834 比例分摊 ----
    @Test
    void multiPolicyIso834_scalesByShare() {
        Map<String, FormulaSnapshot.FeeConfig> m = new LinkedHashMap<>();
        m.put("X", new FormulaSnapshot.FeeConfig("X", Money.of(100), BIG, Money.ZERO, Money.ZERO, null));
        FormulaSnapshot snap = reimb(m);

        Map<String, Money> f = new LinkedHashMap<>();
        f.put("X", Money.of(1000));

        List<ClaimContext.PolicyShare> shares = List.of(
                new ClaimContext.PolicyShare("POL", Money.of(60000)),
                new ClaimContext.PolicyShare("POL2", Money.of(40000)));

        // 默认关闭：net = 全额 1000（单保单独立理算）
        EngineFlags off = flags();
        ClaimContext ctxOff = new ClaimContext("C1", "T1", "P1", "POL", "2026", f, BIG, Money.ZERO, Money.ZERO,
                List.of(), List.of(), Money.ZERO, false, shares);
        IndemnityCalculation offC = engine(off).settle(ctxOff, snap);
        assertEquals(0, Money.of(1000).cmp(offC.netPayout), "默认：单保单全额 1000");

        // 变体开启：本保单保额 60% -> net 折算为 600
        EngineFlags on = flags(); on.multiPolicyIso834 = true;
        ClaimContext ctxOn = new ClaimContext("C2", "T1", "P1", "POL", "2026", f, BIG, Money.ZERO, Money.ZERO,
                List.of(), List.of(), Money.ZERO, false, shares);
        IndemnityCalculation onC = engine(on).settle(ctxOn, snap);
        assertEquals(0, Money.of(600).cmp(onC.netPayout), "变体：多保单 ISO834 折算 60% = 600");
    }

    // ---- #3 多保单 ISO 834 等额分摊（方法二） ----
    @Test
    void multiPolicyIso834_equalMode_splitsFiftyFifty() {
        Map<String, FormulaSnapshot.FeeConfig> m = new LinkedHashMap<>();
        m.put("X", new FormulaSnapshot.FeeConfig("X", Money.of(100), BIG, Money.ZERO, Money.ZERO, null));
        FormulaSnapshot snap = reimb(m);

        Map<String, Money> f = new LinkedHashMap<>();
        f.put("X", Money.of(1000));

        List<ClaimContext.PolicyShare> shares = List.of(
                new ClaimContext.PolicyShare("POL", Money.of(60000)),
                new ClaimContext.PolicyShare("POL2", Money.of(40000)));

        // 等值分摊：两保单各 50%（与保额无关），本保单 net = 500（比例模式曾为 600）
        EngineFlags on = flags(); on.multiPolicyIso834 = true;
        on.multiPolicyIso834Mode = EngineFlags.MultiPolicyMode.EQUAL;
        ClaimContext ctx = new ClaimContext("C3", "T1", "P1", "POL", "2026", f, BIG, Money.ZERO, Money.ZERO,
                List.of(), List.of(), Money.ZERO, false, shares);
        IndemnityCalculation c = engine(on).settle(ctx, snap);
        assertEquals(0, Money.of(500).cmp(c.netPayout), "等值分摊：两保单各 50% = 500（与保额无关）");
    }

    private static Money linePayout(IndemnityCalculation c, String feeType) {
        for (var l : c.lines) if (l.feeType.equals(feeType)) return l.feePayout;
        return Money.ZERO;
    }
}
