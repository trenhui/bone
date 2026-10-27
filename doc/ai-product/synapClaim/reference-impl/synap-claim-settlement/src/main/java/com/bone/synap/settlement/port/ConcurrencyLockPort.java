package com.bone.synap.settlement.port;

import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Supplier;

/**
 * 并发串行锁端口：同 key 串行、跨 key 并发。
 *
 * <p>理算临界区「读累计快照 → 计算额度 → 提交累计」必须原子化：若不在同一临界区内，
 * 并发两请求读到同一「剩余额度」快照后会双双提交，突破年度/责任限额（超赔）。
 * DB 乐观锁（version）只能防「丢失更新」，防不了「基于过期快照的重复占用」，故需此锁兜底。
 *
 * <p>默认 {@link JvmConcurrencyLock}（单实例足够）；集群部署须替换为 DB 租约 / Redis 实现
 * （实现同一接口，引擎编排代码零改动），使跨实例的同保单同周期请求也串行化。
 *
 * <p>业界最佳实践增强（本端口契约）：
 * <ul>
 *   <li><b>有界等待 / fail-fast</b>：{@link #runLocked(String, Supplier, Duration)} 支持超时，
 *       获取锁超过 timeout 即抛 {@link ConcurrencyLockTimeoutException}，绝不无限期阻塞
 *       （避免线程耗尽 / 活锁）。实现方应优先用 tryLock(timeout) 而非无界阻塞。</li>
 *   <li><b>异常安全</b>：临界区抛异常时锁必须释放（本端口所有实现均保证）。</li>
 *   <li><b>跨实例互斥</b>：集群实现基于共享存储（DB 租约 / Redis）使同一 key 跨 JVM 互斥。</li>
 *   <li><b>长临界区续期</b>：持有方应后台心跳刷新租约过期时间，避免租约在长临界区内自然过期被他人抢占。</li>
 * </ul>
 */
public interface ConcurrencyLockPort {

    /** 对给定 key 串行执行 action（同 key 互斥，无界等待）。返回 action 的结果。 */
    <T> T runLocked(String key, Supplier<T> action);

    /**
     * 有界等待版本：获取锁超过 timeout 即抛 {@link ConcurrencyLockTimeoutException}（fail-fast）。
     * 默认实现退化为无界等待；支持超时的实现（如 {@link JvmConcurrencyLock} / 分布式锁）应覆写本方法。
     */
    default <T> T runLocked(String key, Supplier<T> action, Duration timeout) {
        return runLocked(key, action);
    }

    /** 无返回值的便捷重载（无界等待）。 */
    default void runLocked(String key, Runnable action) {
        runLocked(key, () -> { action.run(); return null; });
    }

    /** 无返回值的便捷重载（有界等待）。 */
    default void runLocked(String key, Runnable action, Duration timeout) {
        runLocked(key, () -> { action.run(); return null; }, timeout);
    }

    /** 默认 JVM 实现（单实例足够）。每 key 一个 ReentrantLock，支持有界等待（tryLock）。 */
    class JvmConcurrencyLock implements ConcurrencyLockPort {
        private final ConcurrentHashMap<String, ReentrantLock> monitors = new ConcurrentHashMap<>();

        @Override
        public <T> T runLocked(String key, Supplier<T> action) {
            ReentrantLock m = monitors.computeIfAbsent(key, k -> new ReentrantLock());
            m.lock();
            try {
                return action.get();
            } finally {
                m.unlock();
            }
        }

        @Override
        public <T> T runLocked(String key, Supplier<T> action, Duration timeout) {
            ReentrantLock m = monitors.computeIfAbsent(key, k -> new ReentrantLock());
            boolean acquired;
            try {
                acquired = m.tryLock(timeout.toNanos(), TimeUnit.NANOSECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new ConcurrencyLockTimeoutException(key, timeout, e);
            }
            if (!acquired) throw new ConcurrencyLockTimeoutException(key, timeout);
            try {
                return action.get();
            } finally {
                m.unlock();
            }
        }
    }

    /** 获取锁超时（有界等待 fail-fast）。调用方应据此降级 / 重试，而非无限阻塞。 */
    class ConcurrencyLockTimeoutException extends RuntimeException {
        public final String key;
        public final Duration timeout;

        public ConcurrencyLockTimeoutException(String key, Duration timeout) {
            super("获取并发锁超时 key=" + key + " timeout=" + timeout);
            this.key = key;
            this.timeout = timeout;
        }

        public ConcurrencyLockTimeoutException(String key, Duration timeout, Throwable cause) {
            super("获取并发锁超时 key=" + key + " timeout=" + timeout, cause);
            this.key = key;
            this.timeout = timeout;
        }
    }
}
