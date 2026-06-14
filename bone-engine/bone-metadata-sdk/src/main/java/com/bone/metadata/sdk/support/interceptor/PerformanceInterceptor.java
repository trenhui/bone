package com.bone.metadata.sdk.support.interceptor;

import com.bone.metadata.sdk.domain.annotation.TrackPerformance;
import com.bone.metadata.sdk.support.context.RequestContextHolder;
import java.lang.reflect.Method;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Pointcut;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.core.annotation.Order;

@Aspect
@Order(2)
@Slf4j
public class PerformanceInterceptor {

  /** 拦截所有标注了 @TrackPerformance 的方法 */
  @Pointcut("@annotation(com.bone.metadata.sdk.domain.annotation.TrackPerformance)")
  public void trackPerformancePointcut() {}

  @Around("trackPerformancePointcut()")
  public Object around(ProceedingJoinPoint joinPoint) throws Throwable {
    // 1. 生成唯一 traceId，便于跨日志关联
    String traceId = UUID.randomUUID().toString();
    RequestContextHolder.setTraceId(traceId);

    // 2. 方法签名 & 注解属性
    MethodSignature signature = (MethodSignature) joinPoint.getSignature();
    Method method = signature.getMethod();
    TrackPerformance anno = method.getAnnotation(TrackPerformance.class);
    String category = anno.category();
    boolean logResult = anno.logResult();

    // 3. 参数预处理
    Object[] args = joinPoint.getArgs();
    String params = Arrays.toString(args);

    // 4. 执行并计时
    Instant start = Instant.now();
    Object result = null;
    Throwable error = null;
    try {
      result = joinPoint.proceed();
      return result;
    } catch (Throwable t) {
      error = t;
      throw t;
    } finally {
      Instant end = Instant.now();
      long costMs = Duration.between(start, end).toMillis();

      // 5. 构建日志内容
      StringBuilder msg = new StringBuilder();
      msg.append(
          String.format(
              "[TraceId=%s][%s] %s.%s 执行耗时: %d ms",
              traceId,
              category,
              joinPoint.getTarget().getClass().getSimpleName(),
              method.getName(),
              costMs));
      msg.append("，参数: ").append(params);

      if (error != null) {
        msg.append("，异常: ")
            .append(error.getClass().getSimpleName())
            .append(": ")
            .append(error.getMessage());
        log.error(msg.toString());
      } else {
        if (logResult) {
          msg.append("，返回值: ").append(result);
        }
        log.info(msg.toString());
      }

      // 6. 清理上下文
      RequestContextHolder.clear();
    }
  }
}
