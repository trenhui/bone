package com.bone.engine.extension.core.invoker;

import com.bone.core.exception.BizException;
import com.bone.core.util.ReflectionUtil;
import com.bone.engine.extension.support.metrics.ExtensionMetricsBridge;
import java.lang.reflect.Method;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.lang.NonNull;
import org.springframework.lang.Nullable;

/**
 * 扩展点调用处理器
 *
 * <p>负责处理扩展点方法的调用，提供增强的异常处理、详细的性能指标记录和日志追踪。
 */
public class ExtPointInvocationHandler {

  private static final Logger log = LoggerFactory.getLogger(ExtPointInvocationHandler.class);

  public static Object invoke(
      @NonNull Object target, @NonNull Method method, @Nullable Object[] args) {
    return invoke(target, method, args, resolveExtPointName(target));
  }

  public static Object invoke(
      @NonNull Object target,
      @NonNull Method method,
      @Nullable Object[] args,
      @Nullable String extPointName) {
    validateInvocationParameters(target, method);

    String extensionClassName = target.getClass().getCanonicalName();
    String methodName = method.getName();
    String extPoint = extPointName != null ? extPointName : extensionClassName;
    String impl = target.getClass().getSimpleName();
    String timestamp = LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);

    log.debug(
        "[{}] Starting extension point invocation: {}.{}",
        timestamp,
        extensionClassName,
        methodName);

    long startTime = System.currentTimeMillis();

    try {
      log.debug(
          "[{}] Invoking method: {}.{} with parameter count: {}",
          timestamp,
          extensionClassName,
          methodName,
          args == null ? 0 : args.length);

      Object result = ReflectionUtil.invokeMethod(target, method.getName(), args);

      long executionTime = System.currentTimeMillis() - startTime;
      recordMetrics(extPoint, impl, "success", executionTime);
      log.info(
          "[{}] Extension point invocation successful: {}.{} completed in {}ms",
          timestamp,
          extensionClassName,
          methodName,
          executionTime);

      return result;
    } catch (ReflectionUtil.ReflectionException ex) {
      return handleReflectionException(
          ex, extPoint, impl, extensionClassName, methodName, startTime, timestamp);
    } catch (Exception e) {
      return handleUnexpectedException(
          e, extPoint, impl, extensionClassName, methodName, startTime, timestamp);
    }
  }

  private static void validateInvocationParameters(@NonNull Object target, @NonNull Method method) {
    log.trace("Validating invocation parameters for method: {}", method.getName());
  }

  private static Object handleReflectionException(
      ReflectionUtil.ReflectionException ex,
      String extPoint,
      String impl,
      String className,
      String methodName,
      long startTime,
      String timestamp) {
    long executionTime = System.currentTimeMillis() - startTime;
    Throwable cause = ex.getCause();

    if (cause instanceof BizException) {
      recordMetrics(extPoint, impl, "error", executionTime);
      log.warn(
          "[{}] Business exception in extension point: {}.{} after {}ms - {}",
          timestamp,
          className,
          methodName,
          executionTime,
          cause.getMessage());
      throw (BizException) cause;
    }

    recordMetrics(extPoint, impl, "error", executionTime);
    String errorMsg =
        String.format(
            "Extension point execution failed: %s",
            (cause != null ? cause.getMessage() : ex.getMessage()));

    log.error(
        "[{}] Extension point method invocation failed: {}.{} after {}ms",
        timestamp,
        className,
        methodName,
        executionTime,
        ex);

    throw BizException.of(
        500,
        errorMsg
            + " [extensionPoint="
            + className
            + ", method="
            + methodName
            + ", executionTimeMs="
            + executionTime
            + "]");
  }

  private static Object handleUnexpectedException(
      Exception e,
      String extPoint,
      String impl,
      String className,
      String methodName,
      long startTime,
      String timestamp) {
    long executionTime = System.currentTimeMillis() - startTime;
    recordMetrics(extPoint, impl, "error", executionTime);

    log.error(
        "[{}] Unexpected error during extension point invocation: {}.{} after {}ms",
        timestamp,
        className,
        methodName,
        executionTime,
        e);

    throw BizException.of(
        500,
        "Unexpected error: "
            + e.getMessage()
            + " [extensionPoint="
            + className
            + ", method="
            + methodName
            + ", executionTimeMs="
            + executionTime
            + "]");
  }

  private static void recordMetrics(String extPoint, String impl, String status, long durationMs) {
    ExtensionMetricsBridge.optional()
        .ifPresent(metrics -> metrics.recordInvoke(extPoint, impl, status, durationMs));
  }

  private static String resolveExtPointName(Object target) {
    for (Class<?> iface : target.getClass().getInterfaces()) {
      if (iface.isInterface()) {
        return iface.getName();
      }
    }
    return target.getClass().getName();
  }
}
