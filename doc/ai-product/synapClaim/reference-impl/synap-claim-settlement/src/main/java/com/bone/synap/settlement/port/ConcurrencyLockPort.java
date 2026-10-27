package com.bone.synap.settlement.port;

import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

/**
 * 并发串行锁端口：同 key 串行、跨 key 并发。
 *
 * <p>理算临界区「读累计快照 → 计算额度 → 提交累计」必须原子化：若不在同一临界区内，
 * 并发两请求读到同一「剩余额度」快照后会双双提交，突破年度/责任限额（超赔）。
 * DB 乐观锁（version）只能防「丢失更新」，防不了「基于过期快照的重复占用」，故需此锁兜底。
 *
 * <p>默认 {@link JvmConcurrencyLock}（单实例足够）；集群部署须替换为 Redis/DB 租约实现，
 * 使跨实例的同保单同周期请求也串行化，否则跨实例仍依赖 DB 乐观锁 + 调用方
 * {@code ConcurrentAccumulationException} 重试兜底。
 */
public interface ConcurrencyLockPort {

    /** 对给定 key 串行执行 action（同 key 互斥）。返回 action 的结果。 */
    <T> T runLocked(String key, Supplier<T> action);

    /** 无返回值的便捷重载。 */
    default void runLocked(String key, Runnable action) {
        runLocked(key, () -> { action.run(); return null; });
    }

    /** 默认实现：JVM 内每 key 一个监视器对象 + synchronized。单实例行为与历史一致。 */
    class JvmConcurrencyLock implements ConcurrencyLockPort {
        private final ConcurrentHashMap<String, Object> monitors = new ConcurrentHashMap<>();

        @Override
        public <T> T runLocked(String key, Supplier<T> action) {
            Object m = monitors.computeIfAbsent(key, k -> new Object());
            synchronized (m) {
                return action.get();
            }
        }
    }
}
