package com.bone.core.threadlocal;

import java.util.Map;

/**
 * 支持上下文传递的 Runnable
 */
@FunctionalInterface
public interface TtlRunnable extends Runnable {

    /**
     * 包装普通 Runnable
     */
    static TtlRunnable wrap(Runnable runnable) {
        if (runnable instanceof TtlRunnable) {
            return (TtlRunnable) runnable;
        }
        return new WrappedRunnable(runnable);
    }

    /**
     * 执行任务
     */
    @Override
    void run();

    /**
     * 包装实现
     */
    class WrappedRunnable implements TtlRunnable {
        private final Runnable delegate;
        private final Map<TransmittableThreadLocal<?>, Object> capturedContext;

        public WrappedRunnable(Runnable delegate) {
            this.delegate = delegate;
            this.capturedContext = TransmittableThreadLocal.capture();
        }

        @Override
        public void run() {
            Map<TransmittableThreadLocal<?>, Object> backup = TransmittableThreadLocal.capture();
            try {
                TransmittableThreadLocal.replay(capturedContext);
                delegate.run();
            } finally {
                TransmittableThreadLocal.replay(backup);
            }
        }
    }
}