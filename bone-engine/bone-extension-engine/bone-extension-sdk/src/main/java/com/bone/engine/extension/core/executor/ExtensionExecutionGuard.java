package com.bone.engine.extension.core.executor;

import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.Semaphore;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 扩展调用舱壁 + 超时（JDK 实现，语义对齐 Resilience4j Bulkhead/TimeLimiter）。
 */
public class ExtensionExecutionGuard {

    private final long timeoutMs;
    private final Semaphore permits;
    private final ExecutorService executor;

    public ExtensionExecutionGuard(long timeoutMs, int maxConcurrent) {
        this.timeoutMs = Math.max(1L, timeoutMs);
        this.permits = new Semaphore(Math.max(1, maxConcurrent), true);
        AtomicInteger seq = new AtomicInteger();
        ThreadFactory factory =
                r -> {
                    Thread t = new Thread(r, "ext-exec-guard-" + seq.incrementAndGet());
                    t.setDaemon(true);
                    return t;
                };
        this.executor = Executors.newCachedThreadPool(factory);
    }

    public <T> T execute(Callable<T> task) throws Throwable {
        if (!permits.tryAcquire()) {
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
            permits.release();
        }
    }
}
