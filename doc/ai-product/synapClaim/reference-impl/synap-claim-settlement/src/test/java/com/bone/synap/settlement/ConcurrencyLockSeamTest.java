package com.bone.synap.settlement;

import com.bone.synap.settlement.adapter.jdbc.JdbcBackedPorts;
import com.bone.synap.settlement.domain.ClaimContext;
import com.bone.synap.settlement.domain.FormulaSnapshot;
import com.bone.synap.settlement.engine.EngineFlags;
import com.bone.synap.settlement.engine.SettlementEngine;
import com.bone.synap.settlement.port.ConcurrencyLockPort;
import com.bone.synap.settlement.port.Ports;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.Test;

import javax.sql.DataSource;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 验证理算引擎的并发锁是可插拔接缝（ConcurrencyLockPort），而非写死的 JVM 锁：
 *   - 注入的锁确实被引擎使用（计数为正，证明不是 no-op / 不是默认锁被悄悄替换）；
 *   - 同 key（同保单同周期）严格串行（maxConcurrent == 1），证明串行化语义由注入的锁提供；
 *   - 集群部署只需把默认 JvmConcurrencyLock 换成 Redis/DB 租约实现，引擎编排代码零改动。
 */
public class ConcurrencyLockSeamTest {

    private static final Money BIG = Money.of(1_000_000);

    /** 串行化 + 计数的锁实现：在 synchronized 内更新计数，故同 key 的进入峰值恒为 1。 */
    static final class CountingLock implements ConcurrencyLockPort {
        private final ConcurrentHashMap<String, Object> monitors = new ConcurrentHashMap<>();
        private final ConcurrentHashMap<String, Integer> enter = new ConcurrentHashMap<>();
        final Map<String, Integer> maxConcurrent = new ConcurrentHashMap<>();
        final AtomicInteger total = new AtomicInteger(0);

        @Override
        public <T> T runLocked(String key, Supplier<T> action) {
            Object m = monitors.computeIfAbsent(key, k -> new Object());
            synchronized (m) {
                total.incrementAndGet();
                int cur = enter.merge(key, 1, Integer::sum);
                maxConcurrent.merge(key, cur, Math::max);
                try {
                    return action.get();
                } finally {
                    enter.merge(key, -1, Integer::sum);
                }
            }
        }
    }

    private static DataSource memDs(String name) {
        JdbcDataSource ds = new JdbcDataSource();
        ds.setURL("jdbc:h2:mem:" + name + ";DB_CLOSE_DELAY=-1");
        ds.setUser("sa");
        ds.setPassword("");
        return ds;
    }

    private static SettlementEngine engineWith(DataSource ds, ConcurrencyLockPort lock) throws Exception {
        JdbcBackedPorts.initSchema(ds);
        JdbcBackedPorts p = new JdbcBackedPorts(ds);
        return new SettlementEngine(p, p, p, p, p, p, p, EngineFlags.allOn()).withLock(lock);
    }

    private static FormulaSnapshot reimbSnap(long liabilityLimit) {
        Map<String, FormulaSnapshot.FeeConfig> m = new LinkedHashMap<>();
        m.put("合理金额", new FormulaSnapshot.FeeConfig("合理金额", Money.of(100), BIG, Money.ZERO, Money.ZERO, null));
        return new FormulaSnapshot("L1", "报销型责任", FormulaSnapshot.LiabilityType.REIMBURSEMENT,
                Money.of(liabilityLimit), Money.ZERO, Money.ZERO, Money.ZERO, 0, m, "v1", 1L);
    }

    private static ClaimContext ctx(String claimId, Money fee, Money annualLim) {
        Map<String, Money> f = new LinkedHashMap<>();
        f.put("合理金额", fee);
        return new ClaimContext(claimId, "T1", "P1", "POL", "2026", f, BIG, Money.ZERO, annualLim, List.of());
    }

    @Test
    void injectedLockIsHonoredAndSerializesSameKey() throws Exception {
        CountingLock lock = new CountingLock();
        SettlementEngine e = engineWith(memDs("seamA"), lock);
        final int N = 24;
        ExecutorService pool = Executors.newFixedThreadPool(N);
        CountDownLatch start = new CountDownLatch(1);
        var futures = new java.util.ArrayList<java.util.concurrent.Future<?>>();
        for (int i = 0; i < N; i++) {
            final int idx = i;
            futures.add(pool.submit(() -> {
                start.await();
                e.settle(ctx("C" + idx, Money.of(257 + idx), Money.of(10_000)), reimbSnap(10_000));
                return null;
            }));
        }
        start.countDown();
        for (var f : futures) f.get();
        pool.shutdown();

        // 1) 注入的锁确实被引擎调用（不是 no-op，也不是默认锁被悄悄替换）
        assertTrue(lock.total.get() >= N, "注入锁必须被调用至少 " + N + " 次，实际=" + lock.total.get());
        // 2) 同保单同周期严格串行：任意时刻同一 key 的进入数不超过 1
        assertEquals(1, lock.maxConcurrent.getOrDefault("T1@POL@2026", 0).intValue(),
                "同 key 必须串行（maxConcurrent==1），实际=" + lock.maxConcurrent);
        // 3) 串行化确有效果：年度限额 10000 不被突破（防止基于过期快照的超赔）
        Money dbLimit = col(memDsReopen("seamA"),
                "SELECT limit_used FROM t_settlement_accum WHERE policy_period_key=?", "T1@POL@2026");
        assertTrue(dbLimit.cmp(Money.of(10_000)) <= 0, "年度累计不得突破限额，实际=" + dbLimit);
    }

    @Test
    void swappingLockImplementationIsHonored() throws Exception {
        // 用一个独立计数锁实例替换默认锁，证明引擎服从注入而非写死默认实现。
        CountingLock a = new CountingLock();
        CountingLock b = new CountingLock();
        SettlementEngine e = engineWith(memDs("seamB"), a);
        e.settle(ctx("X1", Money.of(500), Money.of(10_000)), reimbSnap(10_000));
        // a 被调用，b 从未被调用
        assertTrue(a.total.get() >= 1, "注入锁 a 应被调用");
        assertEquals(0, b.total.get(), "未注入的锁 b 不应被调用");
        // 切换到 b 后，调用计入 b
        SettlementEngine e2 = engineWith(memDs("seamC"), b);
        e2.settle(ctx("Y1", Money.of(500), Money.of(10_000)), reimbSnap(10_000));
        assertTrue(b.total.get() >= 1, "切换后注入锁 b 应被调用");
    }

    private static Money col(DataSource ds, String sql, String key) throws Exception {
        try (java.sql.Connection c = ds.getConnection();
             java.sql.PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, key);
            try (java.sql.ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Money.of(rs.getBigDecimal(1)) : Money.ZERO;
            }
        }
    }

    // 复用同一内存库名读取累计结果（H2 mem 同名库共享）
    private static DataSource memDsReopen(String name) {
        return memDs(name);
    }
}
