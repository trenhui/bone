package com.bone.synap.settlement.port;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.SQLIntegrityConstraintViolationException;
import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;

/**
 * 基于共享存储（DB 租约）的分布式并发锁：跨 JVM 实例对同一 key 互斥。
 *
 * <p>适用集群部署——同一保单同周期的「读累计快照→计算额度→提交累计」临界区跨实例串行化，
 * 从根上消除多副本下 JVM 锁失效导致的超赔窗口。与 {@code JvmConcurrencyLock} 实现同一
 * {@link ConcurrencyLockPort} 接口，引擎编排代码零改动（{@code SettlementEngine.withLock(...)} 注入即可）。
 *
 * <p>设计（业界通用 DB 锁模式，<b>不使用 SELECT ... FOR UPDATE</b>——跨实例悲观锁失效且易死锁）：
 * <ul>
 *   <li><b>抢锁</b>：{@code INSERT} 唯一约束(lock_key) 保证原子性，仅一个 owner 成功；
 *       失败则说明已被持有，按租约是否过期决定抢夺。</li>
 *   <li><b>过期回收</b>：获取时发现旧 owner 租约已过期即抢占（条件更新 {@code WHERE expire_at=旧值}），
 *       天然容忍持有者崩溃。</li>
 *   <li><b>续期（看门狗）</b>：持有期间后台心跳刷新 expire_at，避免长临界区租约过期被他人抢走。</li>
 *   <li><b>释放</b>：{@code DELETE WHERE lock_key=? AND owner_id=?}（仅持有者能释放，防误删他人锁）。</li>
 * </ul>
 *
 * <p>零外部依赖：仅用 JDK {@code javax.sql.DataSource} + 纯 JDBC，H2 / MySQL 通用
 * （时间戳以 epoch millis 存 BIGINT，规避各库时间类型差异）。生产建议表按 lock_key 哈希分片 +
 * 独立连接池 + 监控续期成功率。
 */
public class DbLeaseConcurrencyLock implements ConcurrencyLockPort {

    private final DataSource ds;
    private final long defaultTtlMillis;
    private final ScheduledExecutorService watchdog;

    public DbLeaseConcurrencyLock(DataSource ds) {
        this(ds, Duration.ofSeconds(30));
    }

    public DbLeaseConcurrencyLock(DataSource ds, Duration defaultTtl) {
        this.ds = ds;
        this.defaultTtlMillis = defaultTtl.toMillis();
        this.watchdog = Executors.newScheduledThreadPool(1, new ThreadFactory() {
            @Override
            public Thread newThread(Runnable r) {
                Thread t = new Thread(r, "concurrency-lock-watchdog");
                t.setDaemon(true);
                return t;
            }
        });
        initSchema();
    }

    /** 建表（幂等）。也可由 JdbcBackedPorts.initSchema 统一建，此处自保底。 */
    private void initSchema() {
        try (Connection c = ds.getConnection();
             PreparedStatement ps = c.prepareStatement(
                     "CREATE TABLE IF NOT EXISTS lock_lease ("
                             + "lock_key VARCHAR(255) PRIMARY KEY, "
                             + "owner_id VARCHAR(255) NOT NULL, "
                             + "expire_at BIGINT NOT NULL, "
                             + "acquired_at BIGINT NOT NULL)")) {
            ps.execute();
        } catch (SQLException e) {
            throw new IllegalStateException("初始化 lock_lease 表失败", e);
        }
    }

    @Override
    public <T> T runLocked(String key, Supplier<T> action) {
        return runLocked(key, action, Duration.ofMillis(defaultTtlMillis));
    }

    @Override
    public <T> T runLocked(String key, Supplier<T> action, Duration timeout) {
        String owner = UUID.randomUUID().toString();
        long deadlineNanos = System.nanoTime() + timeout.toNanos();
        boolean acquired = tryAcquire(key, owner, timeout);
        while (!acquired && System.nanoTime() < deadlineNanos) {
            try {
                Thread.sleep(Math.min(20, Math.max(1, timeout.toMillis() / 10)));
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                throw new ConcurrencyLockTimeoutException(key, timeout, ie);
            }
            acquired = tryAcquire(key, owner, timeout);
        }
        if (!acquired) throw new ConcurrencyLockTimeoutException(key, timeout);

        Lease lease = new Lease(key, owner, timeout.toMillis());
        watchdog.scheduleAtFixedRate(lease::renew,
                timeout.toMillis() / 3, timeout.toMillis() / 3, TimeUnit.MILLISECONDS);
        try {
            return action.get();
        } finally {
            lease.stop();
            release(key, owner);
        }
    }

    /** INSERT 唯一约束抢锁；冲突且旧租约已过期则抢占。返回是否取得锁。 */
    private boolean tryAcquire(String key, String owner, Duration ttl) {
        long now = System.currentTimeMillis();
        long expire = now + ttl.toMillis();
        try (Connection c = ds.getConnection();
             PreparedStatement ps = c.prepareStatement(
                     "INSERT INTO lock_lease (lock_key, owner_id, expire_at, acquired_at) VALUES (?, ?, ?, ?)")) {
            ps.setString(1, key);
            ps.setString(2, owner);
            ps.setLong(3, expire);
            ps.setLong(4, now);
            ps.executeUpdate();
            return true;
        } catch (SQLIntegrityConstraintViolationException dup) {
            return stealIfExpired(key, owner, expire);
        } catch (SQLException e) {
            throw new IllegalStateException("抢锁失败 key=" + key, e);
        }
    }

    /** 旧 owner 租约已过期时条件更新抢占（WHERE expire_at=旧值，原子，防并发双抢）。 */
    private boolean stealIfExpired(String key, String owner, long newExpire) {
        try (Connection c = ds.getConnection();
             PreparedStatement sel = c.prepareStatement("SELECT expire_at FROM lock_lease WHERE lock_key = ?");
             PreparedStatement upd = c.prepareStatement(
                     "UPDATE lock_lease SET owner_id = ?, expire_at = ?, acquired_at = ? "
                             + "WHERE lock_key = ? AND expire_at = ?")) {
            sel.setString(1, key);
            long oldExpire;
            try (ResultSet rs = sel.executeQuery()) {
                if (!rs.next()) return false; // 已被释放，下一轮 INSERT 会成功
                oldExpire = rs.getLong(1);
            }
            if (oldExpire >= System.currentTimeMillis()) return false; // 仍在有效期内，不抢
            upd.setString(1, owner);
            upd.setLong(2, newExpire);
            upd.setLong(3, System.currentTimeMillis());
            upd.setString(4, key);
            upd.setLong(5, oldExpire);
            return upd.executeUpdate() == 1;
        } catch (SQLException e) {
            return false; // 抢占失败（并发/连接异常），下一轮重试
        }
    }

    /** 仅持有者能释放自身锁，防误删他人锁。 */
    private void release(String key, String owner) {
        try (Connection c = ds.getConnection();
             PreparedStatement ps = c.prepareStatement(
                     "DELETE FROM lock_lease WHERE lock_key = ? AND owner_id = ?")) {
            ps.setString(1, key);
            ps.setString(2, owner);
            ps.executeUpdate();
        } catch (SQLException ignored) {
            // 释放失败最差是租约自然过期；生产可接 TracePort 记录
        }
    }

    /** 停止后台续期线程池（测试/关停时调用）。 */
    public void shutdown() {
        watchdog.shutdownNow();
    }

    /** 持有的租约：后台心跳续期至 now+ttl。 */
    private final class Lease {
        private final String key;
        private final String owner;
        private final long ttlMillis;
        private final AtomicBoolean stopped = new AtomicBoolean(false);

        Lease(String key, String owner, long ttlMillis) {
            this.key = key;
            this.owner = owner;
            this.ttlMillis = ttlMillis;
        }

        void renew() {
            if (stopped.get()) return;
            try (Connection c = ds.getConnection();
                 PreparedStatement ps = c.prepareStatement(
                         "UPDATE lock_lease SET expire_at = ? WHERE lock_key = ? AND owner_id = ?")) {
                ps.setLong(1, System.currentTimeMillis() + ttlMillis);
                ps.setString(2, key);
                ps.setString(3, owner);
                ps.executeUpdate();
            } catch (SQLException ignored) {
                // 续期失败最差是租约自然过期；看门狗下轮重试
            }
        }

        void stop() {
            stopped.set(true);
        }
    }
}
