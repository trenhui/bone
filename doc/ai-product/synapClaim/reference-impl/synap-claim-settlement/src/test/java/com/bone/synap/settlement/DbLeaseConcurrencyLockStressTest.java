package com.bone.synap.settlement;

import com.bone.synap.settlement.adapter.jdbc.JdbcBackedPorts;
import com.bone.synap.settlement.domain.ClaimContext;
import com.bone.synap.settlement.domain.FormulaSnapshot;
import com.bone.synap.settlement.domain.IndemnityCalculation;
import com.bone.synap.settlement.engine.EngineFlags;
import com.bone.synap.settlement.engine.SettlementEngine;
import com.bone.synap.settlement.port.ConcurrencyLockPort;
import com.bone.synap.settlement.port.DbLeaseConcurrencyLock;
import org.h2.jdbcx.JdbcDataSource;

import javax.sql.DataSource;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * 分布式锁（DbLeaseConcurrencyLock）负载级压测：两个共享同一 H2 库的引擎实例（模拟集群两节点），
 * 以并发线程猛攻同一保单同周期，验证跨实例串行化在真实负载下仍成立——
 * 零超赔、零丢失更新、零乐观锁冲突。若锁失效，冲突会冒泡为异常使测试 fail-closed 转红（防假绿）。
 */
public class DbLeaseConcurrencyLockStressTest {

    private static final Money BIG = Money.of(1_000_000);

    private static DataSource memDs(String name) {
        JdbcDataSource ds = new JdbcDataSource();
        ds.setURL("jdbc:h2:mem:" + name + ";DB_CLOSE_DELAY=-1;LOCK_TIMEOUT=60000");
        ds.setUser("sa");
        ds.setPassword("");
        return ds;
    }

    private static FormulaSnapshot reimbSnap(long annualLim) {
        Map<String, FormulaSnapshot.FeeConfig> m = new LinkedHashMap<>();
        m.put("合理金额", new FormulaSnapshot.FeeConfig("合理金额", Money.of(100), Money.of(annualLim), Money.ZERO, Money.ZERO, null));
        return new FormulaSnapshot("L1", "报销型责任", FormulaSnapshot.LiabilityType.REIMBURSEMENT,
                Money.ZERO, Money.ZERO, Money.ZERO, Money.of(annualLim), 0, m, "v1", 1L);
    }

    private static ClaimContext ctx(String claimId, Money fee, Money annualLim) {
        Map<String, Money> f = new LinkedHashMap<>();
        f.put("合理金额", fee);
        return new ClaimContext(claimId, "T1", "P1", "POL", "2026", f, BIG, Money.ZERO, annualLim, List.of());
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

    private static void assertMoney(Money expected, Money actual, String msg) {
        assertEquals(0, expected.cmp(actual), msg + " [期望 " + expected + " 实际 " + actual + "]");
    }

    /** 两个引擎实例共享同一 DS（集群两节点），均通过 withLock 接入同一分布式锁实现。 */
    @Test
    void clusterTwoNodesNoOverLimitUnderContention() throws Exception {
        final int N = 40;
        final long ANNUAL = 10_000;
        DataSource ds = memDs("dbLeaseStress");
        JdbcBackedPorts.initSchema(ds);

        EngineFlags flags = EngineFlags.allOn();
        flags.applyIdempotencyCAS = false; // 各案 claimId 唯一，关注跨实例累计并发；幂等 CAS 非本场景重点

        // 两节点 = 两个引擎 + 两个分布式锁实例，共享同一库与同一 lock_lease 表
        JdbcBackedPorts pA = new JdbcBackedPorts(ds);
        JdbcBackedPorts pB = new JdbcBackedPorts(ds);
        DbLeaseConcurrencyLock lockA = new DbLeaseConcurrencyLock(ds);
        DbLeaseConcurrencyLock lockB = new DbLeaseConcurrencyLock(ds);
        SettlementEngine nodeA = new SettlementEngine(pA, pA, pA, pA, pA, pA, pA, flags).withLock(lockA);
        SettlementEngine nodeB = new SettlementEngine(pB, pB, pB, pB, pB, pB, pB, flags).withLock(lockB);
        FormulaSnapshot snap = reimbSnap(ANNUAL);

        ExecutorService pool = Executors.newFixedThreadPool(N);
        CountDownLatch start = new CountDownLatch(1);
        Money[] capNet = new Money[N];
        Money[] capLim = new Money[N];
        List<Throwable> errors = new ArrayList<>();
        for (int i = 0; i < N; i++) {
            final int idx = i;
            // 轮流交由两个节点执行，模拟跨实例并发
            final SettlementEngine node = (i % 2 == 0) ? nodeA : nodeB;
            final Money fee = Money.of(257 + idx);
            pool.submit(() -> {
                try {
                    start.await();
                    IndemnityCalculation c = node.settle(ctx("E" + idx, fee, Money.of(ANNUAL)), snap);
                    capNet[idx] = c.netPayout;
                    capLim[idx] = c.annualLimitApplied;
                } catch (Throwable t) {
                    synchronized (errors) { errors.add(t); }
                }
            });
        }
        start.countDown();
        pool.shutdown();
        try {
            assertTrue(pool.awaitTermination(60, TimeUnit.SECONDS), "集群并发理算未在 60s 内完成");
        } finally {
            lockA.shutdown();
            lockB.shutdown();
        }
        // 分布式锁生效时：跨实例并发不得抛出任何异常（含乐观锁 CONFLICT），否则即锁失效
        assertTrue(errors.isEmpty(), "跨实例并发理算不得抛异常（分布式锁应消除冲突），实际: " + errors);

        Money sumNet = Money.ZERO, sumLim = Money.ZERO;
        int nonNull = 0;
        for (int i = 0; i < N; i++) {
            assertTrue(capNet[i] != null && capLim[i] != null, "E" + i + " 结算缺失");
            sumNet = sumNet.add(capNet[i]);
            sumLim = sumLim.add(capLim[i]);
            nonNull++;
        }
        assertEquals(N, nonNull);

        Money dbLimitUsed = col(ds,
                "SELECT limit_used FROM t_settlement_accum WHERE policy_period_key=?", "T1@POL@2026");
        // (1) 无丢失更新：Σ 各案占用 == 库内累计（跨实例仍一致）
        assertMoney(sumLim, dbLimitUsed, "集群跨实例并发累计必须无丢失更新（Σ占用==库内累计）");
        // (2) 不突破年度限额（基于过期快照的重复占用会使其 > ANNUAL）
        assertTrue(dbLimitUsed.cmp(Money.of(ANNUAL)) <= 0, "年度限额不得被突破，实际=" + dbLimitUsed);
        // (3) 净赔付与限额占用一致（100% 比例、无免赔、无第三方已付）
        assertMoney(sumNet, sumLim, "净赔付应等于限额占用（本场景无免赔/第三方抵扣）");
    }
}
