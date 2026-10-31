package com.bone.synap.settlement;

import com.bone.synap.settlement.port.ConcurrencyLockPort;
import com.bone.synap.settlement.port.DbLeaseConcurrencyLock;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.Test;

import javax.sql.DataSource;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * 验证 DbLeaseConcurrencyLock（基于共享存储的分布式锁）跨 JVM 实例互斥 + 有界等待 fail-fast。
 * 两个 lock 实例共享同一 H2 库（模拟集群两个节点），证明同 key 跨实例串行化。
 */
public class DbLeaseConcurrencyLockTest {

    private static DataSource memDs(String name) {
        JdbcDataSource ds = new JdbcDataSource();
        ds.setURL("jdbc:h2:mem:" + name + ";DB_CLOSE_DELAY=-1");
        ds.setUser("sa");
        ds.setPassword("");
        return ds;
    }

    @Test
    void sameKeySerializesAcrossInstances() throws Exception {
        DataSource ds = memDs("dbLease1");
        DbLeaseConcurrencyLock a = new DbLeaseConcurrencyLock(ds);
        DbLeaseConcurrencyLock b = new DbLeaseConcurrencyLock(ds); // 模拟集群另一节点
        try {
            String key = "T1@POL@2026";
            AtomicInteger inside = new AtomicInteger(0);
            AtomicInteger maxInside = new AtomicInteger(0);
            final int N = 30;
            ExecutorService pool = Executors.newFixedThreadPool(N);
            CountDownLatch start = new CountDownLatch(1);
            List<java.util.concurrent.Future<?>> fs = new ArrayList<>();
            for (int i = 0; i < N; i++) {
                final DbLeaseConcurrencyLock inst = (i % 2 == 0) ? a : b;
                fs.add(pool.submit(() -> {
                    start.await();
                    inst.runLocked(key, () -> {
                        int cur = inside.incrementAndGet();
                        maxInside.accumulateAndGet(cur, Math::max);
                        try {
                            Thread.sleep(3);
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                        }
                        inside.decrementAndGet();
                        return null;
                    });
                    return null;
                }));
            }
            start.countDown();
            for (var f : fs) f.get();
            pool.shutdown();
            // 跨实例同 key 必须串行：任意时刻只有一个实例的临界区在执行
            assertEquals(1, maxInside.get(), "跨实例同 key 必须串行（maxInside==1），实际=" + maxInside.get());
        } finally {
            a.shutdown();
            b.shutdown();
        }
    }

    @Test
    void timeoutFailsFastWhenContended() throws Exception {
        DataSource ds = memDs("dbLease2");
        DbLeaseConcurrencyLock holder = new DbLeaseConcurrencyLock(ds);
        DbLeaseConcurrencyLock waiter = new DbLeaseConcurrencyLock(ds);
        ExecutorService h = Executors.newSingleThreadExecutor();
        try {
            String key = "HOT";
            h.submit(() -> holder.runLocked(key, () -> {
                try {
                    Thread.sleep(1500);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                return null;
            }));
            Thread.sleep(150); // 确保 holder 已持锁（租约未过期）
            // 持锁方未释放且租约未过期时，短超时等待必须 fail-fast 抛超时异常（绝不无限阻塞）
            assertThrows(ConcurrencyLockPort.ConcurrencyLockTimeoutException.class,
                    () -> waiter.runLocked(key, () -> null, Duration.ofMillis(200)),
                    "持锁方未释放且未过期时，短超时等待必须 fail-fast 抛超时异常");
        } finally {
            h.shutdownNow();
            holder.shutdown();
            waiter.shutdown();
        }
    }
}
