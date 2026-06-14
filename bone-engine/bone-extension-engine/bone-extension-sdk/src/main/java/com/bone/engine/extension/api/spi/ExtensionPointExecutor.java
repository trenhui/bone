package com.bone.engine.extension.api.spi;

import com.bone.engine.extension.support.context.BizContext;
import java.lang.reflect.Method;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Lazy;
import org.springframework.core.task.AsyncTaskExecutor;

// com.bone.extension.api.spi.ExtensionPointExecutor
public interface ExtensionPointExecutor {
  <T> T execute(Object implementation, BizContext<?> context, Method method, Object[] args)
      throws Throwable;

  /**
   * 默认异步执行方法，使用配置的线程池
   *
   * <p>使用 {@link
   * com.bone.engine.extension.support.config.ExtensionAsyncConfig#EXTENSION_ASYNC_EXECUTOR_BEAN_NAME}
   * 指定的线程池执行异步任务，避免使用默认的ForkJoinPool.commonPool()
   *
   * @param impl 扩展实现
   * @param ctx 业务上下文
   * @param method 方法
   * @param args 参数
   * @param returnType 返回类型
   * @param asyncExecutor 异步执行器，可通过Spring注入
   * @param <T> 返回类型泛型
   * @return CompletableFuture包装的结果
   */
  default <T> CompletableFuture<T> executeAsync(
      Object impl,
      BizContext<?> ctx,
      Method method,
      Object[] args,
      Class<T> returnType,
      @Lazy @Qualifier("extensionAsyncExecutor") AsyncTaskExecutor asyncExecutor) {
    return CompletableFuture.supplyAsync(
        () -> {
          try {
            return returnType.cast(execute(impl, ctx, method, args));
          } catch (Throwable e) {
            throw new CompletionException(e);
          }
        },
        asyncExecutor);
  }

  /**
   * 兼容旧版本的异步执行方法
   *
   * <p>当没有提供线程池时，使用默认的ForkJoinPool.commonPool()
   */
  default <T> CompletableFuture<T> executeAsync(
      Object impl, BizContext<?> ctx, Method method, Object[] args, Class<T> returnType) {
    return CompletableFuture.supplyAsync(
        () -> {
          try {
            return returnType.cast(execute(impl, ctx, method, args));
          } catch (Throwable e) {
            throw new CompletionException(e);
          }
        });
  }
}
