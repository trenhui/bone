package com.bone.core.threadlocal;

import java.util.Map;
import java.util.concurrent.Callable;

/** TransmittableThreadLocal 工具类 */
public final class TtlUtil {

  private TtlUtil() {
    // 工具类，禁止实例化
  }

  /** 包装 Runnable */
  public static Runnable wrap(Runnable runnable) {
    return TtlRunnable.wrap(runnable);
  }

  /** 包装 Callable */
  public static <V> Callable<V> wrap(Callable<V> callable) {
    return TtlCallable.wrap(callable);
  }

  /** 清除所有 TransmittableThreadLocal */
  public static void clear() {
    TransmittableThreadLocal.clear();
  }

  /** 捕获当前上下文 */
  public static Map<TransmittableThreadLocal<?>, Object> capture() {
    return TransmittableThreadLocal.capture();
  }

  /** 恢复上下文 */
  public static void replay(Map<TransmittableThreadLocal<?>, Object> context) {
    TransmittableThreadLocal.replay(context);
  }

  /** 在当前线程中执行带上下文的任务 */
  public static void runWithContext(
      Runnable task, Map<TransmittableThreadLocal<?>, Object> context) {
    Map<TransmittableThreadLocal<?>, Object> backup = TransmittableThreadLocal.capture();
    try {
      TransmittableThreadLocal.replay(context);
      task.run();
    } finally {
      TransmittableThreadLocal.replay(backup);
    }
  }

  /** 在当前线程中执行带上下文的 Callable */
  public static <V> V callWithContext(
      Callable<V> task, Map<TransmittableThreadLocal<?>, Object> context) throws Exception {
    Map<TransmittableThreadLocal<?>, Object> backup = TransmittableThreadLocal.capture();
    try {
      TransmittableThreadLocal.replay(context);
      return task.call();
    } finally {
      TransmittableThreadLocal.replay(backup);
    }
  }
}
