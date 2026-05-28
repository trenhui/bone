package com.bone.engine.extension.core.executor;

import com.bone.engine.extension.core.metrics.ExtensionMetricsCollector;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.Semaphore;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.lang.Nullable;

/**
 * 扩展调用舱壁 + 超时（JDK 实现，语义对齐 Resilience4j Bulkhead/TimeLimiter）。
 *
 * <p>支持全局舱壁与按插件（bulkheadKey）隔离舱壁。
 */
public class ExtensionExecutionGuard {

    private final long timeoutMs;
    private final Semaphore globalPermits;
    private final boolean perPluginBulkheadEnabled;
    private final int perPluginMaxConcurrent;
    private final ConcurrentHashMap<String, Semaphore> perPluginPermits = new ConcurrentHashMap<>();
    private final ExecutorService executor;
    @Nullable private final ExtensionMetricsCollector metricsCollector;

    public ExtensionExecutionGuard(
            long timeoutMs,
            int maxConcurrent,
            boolean perPluginBulkheadEnabled,
            int perPluginMaxConcurrent,
            @Nullable ExtensionMetricsCollector metricsCollector) {
        this.timeoutMs = Math.max(1L, timeoutMs);
        this.globalPermits = new Semaphore(Math.max(1, maxConcurrent), true);
        this.perPluginBulkheadEnabled = perPluginBulkheadEnabled;
        this.perPluginMaxConcurrent = Math.max(1, perPluginMaxConcurrent);
        this.metricsCollector = metricsCollector;
        AtomicInteger seq = new AtomicInteger();
        ThreadFactory factory =
                r -> {
                    Thread t = new Thread(r, "ext-exec-guard-" + seq.incrementAndGet());
                    t.setDaemon(true);
                    return t;
                };
        this.executor = Executors.newCachedThreadPool(factory);
    }

    public ExtensionExecutionGuard(long timeoutMs, int maxConcurrent) {
        this(timeoutMs, maxConcurrent, false, 4, null);
    }

    public <T> T execute(Callable<T> task) throws Throwable {
        return execute(task, "global");
    }

    public <T> T execute(Callable<T> task, String bulkheadKey) throws Throwable {
        String key = bulkheadKey == null || bulkheadKey.isBlank() ? "global" : bulkheadKey;
        Semaphore pluginPermit = null;
        if (perPluginBulkheadEnabled && !"global".equals(key)) {
            pluginPermit = perPluginPermits.computeIfAbsent(key, k -> new Semaphore(perPluginMaxConcurrent, true));
            if (!pluginPermit.tryAcquire()) {
                recordBulkheadRejected(key);
                throw new IllegalStateException("扩展插件舱壁已满，请稍后重试 [" + key + "]");
            }
        }
        if (!globalPermits.tryAcquire()) {
            if (pluginPermit != null) {
                pluginPermit.release();
            }
            recordBulkheadRejected("global");
            throw new IllegalStateException("扩展执行舱壁已满，请稍后重试");
        }
        Future<T> future = executor.submit(task);
        try {
            return future.get(timeoutMs, TimeUnit.MILLISECONDS);
        } catch (TimeoutException ex) {
            future.cancel(true);
            throw new TimeoutException("扩展执行超时（>" + timeoutMs + "ms）");
        } catch (ExecutionException ex) {
            Throwable cause = ex.getCause();
            if (cause instanceof Error error) {
                throw error;
            }
            if (cause != null) {
                throw cause;
            }
            throw ex;
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw ex;
        } finally {
            globalPermits.release();
            if (pluginPermit != null) {
                pluginPermit.release();
            }
        }
    }

    private void recordBulkheadRejected(String bulkheadKey) {
        if (metricsCollector != null) {
            metricsCollector.recordBulkheadRejected(bulkheadKey);
        }
    }
}
