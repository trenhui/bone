package com.bone.metadata.sdk.support.interceptor;

import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;

import java.util.concurrent.TimeUnit;

/**
 * 扩展字段操作监控切面。
 */
@Aspect
@Slf4j
public class ExtensionMonitorInterceptor {

    @Around("execution(* com.bone.metadata.sdk.extension..*(..))")
    public Object monitorExtensionOperations(ProceedingJoinPoint joinPoint) throws Throwable {
        String method = joinPoint.getSignature().toShortString();
        long start = System.nanoTime();
        boolean success = false;

        try {
            Object result = joinPoint.proceed();
            success = true;
            return result;
        } finally {
            long duration = System.nanoTime() - start;
            if (duration > TimeUnit.MILLISECONDS.toNanos(500)) {
                log.warn("Slow extension operation: {} took {}ms", method, TimeUnit.NANOSECONDS.toMillis(duration));
            }
        }
    }
}