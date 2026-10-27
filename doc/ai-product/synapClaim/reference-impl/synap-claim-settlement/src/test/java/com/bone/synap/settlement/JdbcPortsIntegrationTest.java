package com.bone.synap.settlement;

import com.bone.synap.settlement.adapter.jdbc.JdbcBackedPorts;
import com.bone.synap.settlement.domain.ClaimContext;
import com.bone.synap.settlement.domain.FormulaSnapshot;
import com.bone.synap.settlement.domain.IndemnityCalculation;
import com.bone.synap.settlement.engine.SettlementEngine;
import com.bone.synap.settlement.port.Ports;
import org.h2.jdbcx.JdbcDataSource;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 生产级 JDBC 端口集成测试（H2 内存库）。
 * 验证理算引擎挂载真实存储后的关键资金安全路径：
 *   - 责任限额跨案累计并持久化到库（N1）
 *   - 重算/撤件冲正回退累计占用（N2/N8）
 *   - 预授权冻结额度计入三值模型，capture/void 释放（N3 + 亿保即时结算）
 *   - 幂等 CAS 租约（N7）
 *   - 累计并发乐观锁冲突 fail-closed（CONFLICT，禁静默覆盖）
 *
 * 注：Money 为不可变值对象且不在本测试范围内改 equals，所有金额断言统一用 cmp 比较数值。
 */
class JdbcPortsIntegrationTest {

    private static final Money BIG = Money.of(1_000_000);

    private static void assertMoney(Money expected, Money actual, String msg) {
        assertEquals(0, expected.cmp(actual), msg + " [期望 " + expected + " 实际 " + actual + "]");
    }

    private static DataSource memDs(String name) {
        JdbcDataSource ds = new JdbcDataSource();
        ds.setURL("jdbc:h2:mem:" + name + ";DB_CLOSE_DELAY=-1");
        ds.setUser("sa");
        ds.setPassword("");
        return ds;
    }

    private static SettlementEngine engine(DataSource ds) throws Exception {
        JdbcBackedPorts.initSchema(ds);
        JdbcBackedPorts p = new JdbcBackedPorts(ds);
        return new SettlementEngine(p, p, p, p, p, p, p);
    }

    private static FormulaSnapshot reimbSnap(long liabilityLimit) {
        Map<String, FormulaSnapshot.FeeConfig> m = new LinkedHashMap<>();
        m.put("合理金额", new FormulaSnapshot.FeeConfig("合理金额", Money.of(100), BIG, Money.ZERO, Money.ZERO, null));
        return new FormulaSnapshot("L1", "报销型责任", FormulaSnapshot.LiabilityType.REIMBURSEMENT,
                Money.of(liabilityLimit), Money.ZERO, Money.ZERO, Money.ZERO, 0, m, "v1", 1L);
    }

    private static ClaimContext ctx(String claimId, String policyId, Money fee,
                                    Money planRem, Money annualDed, Money annualLim) {
        Map<String, Money> f = new LinkedHashMap<>();
        f.put("合理金额", fee);
        return new ClaimContext(claimId, "T1", "P1", policyId, "2026", f, planRem, annualDed, annualLim, List.of());
    }

    private static Money col(DataSource ds, String sql, String key) throws Exception {
        try (Connection c = ds.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, key);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Money.of(rs.getBigDecimal(1)) : Money.ZERO;
            }
        }
    }

    private static String preauthStatus(DataSource ds, String authId) throws Exception {
        try (Connection c = ds.getConnection();
             PreparedStatement ps = c.prepareStatement("SELECT status FROM t_preauth WHERE auth_id=?")) {
            ps.setString(1, authId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getString(1) : null;
            }
        }
    }

    @Test
    void liabilityLimitAccumulatesCrossCaseAndPersists() throws Exception {
        DataSource ds = memDs("acc");
        SettlementEngine e = engine(ds);
        FormulaSnapshot snap = reimbSnap(5000);
        IndemnityCalculation c1 = e.settle(ctx("C1", "POL", Money.of(4000), BIG, Money.ZERO, Money.ZERO), snap);
        IndemnityCalculation c2 = e.settle(ctx("C2", "POL", Money.of(4000), BIG, Money.ZERO, Money.ZERO), snap);

        assertMoney(Money.of(4000), c1.netPayout, "首案应使用满额 4000");
        assertMoney(Money.of(1000), c2.netPayout, "次案仅余 1000 责任限额");

        assertMoney(Money.of(5000),
                col(ds, "SELECT liability_used FROM t_settlement_accum WHERE policy_period_key=?", "T1@POL@2026"),
                "责任限额累计应持久化为 5000");

        IndemnityCalculation c1Again = e.settle(ctx("C1", "POL", Money.of(4000), BIG, Money.ZERO, Money.ZERO), snap);
        assertMoney(c1.netPayout, c1Again.netPayout, "幂等应返回相同结论");
        assertMoney(Money.of(5000),
                col(ds, "SELECT liability_used FROM t_settlement_accum WHERE policy_period_key=?", "T1@POL@2026"),
                "重算不应双计累计");
    }

    @Test
    void reversalRestoresQuota() throws Exception {
        DataSource ds = memDs("rev");
        SettlementEngine e = engine(ds);
        FormulaSnapshot snap = reimbSnap(10000);
        IndemnityCalculation c = e.settle(ctx("C-REV", "POL", Money.of(3000), BIG, Money.ZERO, Money.ZERO), snap);
        assertMoney(Money.of(3000), c.netPayout, "首案赔付 3000");
        assertMoney(Money.of(3000),
                col(ds, "SELECT liability_used FROM t_settlement_accum WHERE policy_period_key=?", "T1@POL@2026"),
                "累计占用应为 3000");

        e.reverseSettlement(c);
        assertMoney(Money.ZERO,
                col(ds, "SELECT liability_used FROM t_settlement_accum WHERE policy_period_key=?", "T1@POL@2026"),
                "冲正后累计占用应归零");
    }

    @Test
    void preAuthFreezeThenCapture() throws Exception {
        DataSource ds = memDs("pa");
        SettlementEngine e = engine(ds);
        FormulaSnapshot snap = reimbSnap(1_000_000L);
        SettlementEngine.PreAuthResult r = e.preAuthorizeAndHold(
                ctx("C-PA", "POL", Money.ZERO, BIG, Money.ZERO, Money.of(10000)), snap, Money.of(6000), 60_000L);

        assertTrue(r.authorizedAmount.isPositive(), "应产生正数预授权");
        assertEquals("HELD", preauthStatus(ds, r.authId));
        assertMoney(r.authorizedAmount,
                col(ds, "SELECT frozen FROM t_settlement_accum WHERE policy_period_key=?", "T1@POL@2026"),
                "预授权应冻结等额额度（三值模型 frozen 维度）");

        e.capturePreAuth(r.authId, r.authorizedAmount);
        assertEquals("CAPTURED", preauthStatus(ds, r.authId));
        assertMoney(Money.ZERO,
                col(ds, "SELECT frozen FROM t_settlement_accum WHERE policy_period_key=?", "T1@POL@2026"),
                "结算扣减后冻结额度应释放");
    }

    @Test
    void preAuthVoidReleasesFrozen() throws Exception {
        DataSource ds = memDs("pav");
        SettlementEngine e = engine(ds);
        FormulaSnapshot snap = reimbSnap(1_000_000L);
        SettlementEngine.PreAuthResult r = e.preAuthorizeAndHold(
                ctx("C-PA", "POL", Money.ZERO, BIG, Money.ZERO, Money.of(10000)), snap, Money.of(6000), 60_000L);
        assertTrue(r.authorizedAmount.isPositive(), "应产生正数预授权");

        e.voidPreAuth(r.authId);
        assertEquals("VOIDED", preauthStatus(ds, r.authId));
        assertMoney(Money.ZERO,
                col(ds, "SELECT frozen FROM t_settlement_accum WHERE policy_period_key=?", "T1@POL@2026"),
                "撤销预授权应释放冻结额度");
    }

    @Test
    void idempotencyLeaseAllowsSingleOwner() throws Exception {
        DataSource ds = memDs("idem");
        JdbcBackedPorts.initSchema(ds);
        JdbcBackedPorts p = new JdbcBackedPorts(ds);
        assertTrue(p.tryBegin("K1"), "首个请求应取得租约");
        assertFalse(p.tryBegin("K1"), "并发同键请求应被拒绝（fail-closed）");
    }

    @Test
    void concurrentAccumulationConflictIsFailClosed() throws Exception {
        DataSource ds = memDs("conf");
        JdbcBackedPorts.initSchema(ds);
        JdbcBackedPorts p = new JdbcBackedPorts(ds);
        Ports.AccUsage u0 = p.getUsage("T1@POL-C@2026", "2026");
        Ports.CommitResult first = p.commitUsage("T1@POL-C@2026", "2026", "X1",
                Money.of(100), Money.ZERO, Money.ZERO, u0.version);
        // 第二个请求拿着过期版本号（模拟并发读到同一旧版本）
        Ports.CommitResult stale = p.commitUsage("T1@POL-C@2026", "2026", "X2",
                Money.of(100), Money.ZERO, Money.ZERO, u0.version);
        assertEquals(Ports.CommitResult.COMMITTED, first);
        assertEquals(Ports.CommitResult.CONFLICT, stale, "并发乐观锁冲突必须 CONFLICT（禁静默覆盖）");
    }
}
