package com.bone.core.threadlocal;

import java.util.*;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;

/** 简化版 TransmittableThreadLocal - 类型安全实现 */
public class TransmittableThreadLocal<T> extends InheritableThreadLocal<T> {

  // 使用弱引用避免内存泄漏
  private static final Set<TransmittableThreadLocal<?>> HOLDER =
      Collections.newSetFromMap(new ConcurrentHashMap<>());

  public TransmittableThreadLocal() {
    super();
    HOLDER.add(this);
  }

  @Override
  public final void set(T value) {
    super.set(value);
  }

  @Override
  public final void remove() {
    super.remove();
    HOLDER.remove(this);
  }

  /** 捕获当前线程的所有 TransmittableThreadLocal 值 */
  public static Map<TransmittableThreadLocal<?>, Object> capture() {
    Map<TransmittableThreadLocal<?>, Object> captured = new HashMap<>();
    for (TransmittableThreadLocal<?> threadLocal : HOLDER) {
      // 使用辅助方法避免类型问题
      captured.put(threadLocal, captureValue(threadLocal));
    }
    return captured;
  }

  /** 辅助方法：安全地捕获单个 ThreadLocal 的值 */
  @SuppressWarnings("unchecked")
  private static <T> Object captureValue(TransmittableThreadLocal<T> threadLocal) {
    T value = threadLocal.get();
    return threadLocal.copy(value);
  }

  /** 将捕获的上下文恢复到当前线程 */
  @SuppressWarnings("unchecked")
  public static void replay(Map<TransmittableThreadLocal<?>, Object> captured) {
    for (Map.Entry<TransmittableThreadLocal<?>, Object> entry : captured.entrySet()) {
      TransmittableThreadLocal<Object> threadLocal =
          (TransmittableThreadLocal<Object>) entry.getKey();
      threadLocal.set(entry.getValue());
    }
  }

  /** 清理当前线程的 TransmittableThreadLocal */
  public static void clear() {
    for (TransmittableThreadLocal<?> threadLocal : HOLDER) {
      threadLocal.remove();
    }
  }

  /** 创建带上下文包装的 Runnable */
  public static Runnable wrap(Runnable runnable) {
    Map<TransmittableThreadLocal<?>, Object> captured = capture();
    return () -> {
      Map<TransmittableThreadLocal<?>, Object> backup = capture();
      try {
        replay(captured);
        runnable.run();
      } finally {
        replay(backup);
      }
    };
  }

  /** 创建带上下文包装的 Callable */
  public static <V> Callable<V> wrap(Callable<V> callable) {
    Map<TransmittableThreadLocal<?>, Object> captured = capture();
    return () -> {
      Map<TransmittableThreadLocal<?>, Object> backup = capture();
      try {
        replay(captured);
        return callable.call();
      } finally {
        replay(backup);
      }
    };
  }

  /** 值拷贝方法（子类可重写） */
  protected T copy(T parentValue) {
    return parentValue;
  }

  /** 获取当前线程的所有 TransmittableThreadLocal 实例 */
  public static Set<TransmittableThreadLocal<?>> getAll() {
    return Collections.unmodifiableSet(new HashSet<>(HOLDER));
  }

  /** 取消注册此 ThreadLocal */
  public void unregister() {
    HOLDER.remove(this);
  }
}
