package com.bone.core.threadlocal;

import java.util.Map;
import java.util.concurrent.Callable;

/** 支持上下文传递的 Callable */
@FunctionalInterface
public interface TtlCallable<V> extends Callable<V> {

  /** 包装普通 Callable */
  static <V> TtlCallable<V> wrap(Callable<V> callable) {
    if (callable instanceof TtlCallable) {
      return (TtlCallable<V>) callable;
    }
    return new WrappedCallable<>(callable);
  }

  /** 执行任务 */
  @Override
  V call() throws Exception;

  /** 包装实现 */
  class WrappedCallable<V> implements TtlCallable<V> {
    private final Callable<V> delegate;
    private final Map<TransmittableThreadLocal<?>, Object> capturedContext;

    public WrappedCallable(Callable<V> delegate) {
      this.delegate = delegate;
      this.capturedContext = TransmittableThreadLocal.capture();
    }

    @Override
    public V call() throws Exception {
      Map<TransmittableThreadLocal<?>, Object> backup = TransmittableThreadLocal.capture();
      try {
        TransmittableThreadLocal.replay(capturedContext);
        return delegate.call();
      } finally {
        TransmittableThreadLocal.replay(backup);
      }
    }
  }
}
